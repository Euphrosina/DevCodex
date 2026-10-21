package ai.devcodex.agent.agent

import ai.devcodex.agent.data.AppSettings
import ai.devcodex.agent.data.ChatSession
import ai.devcodex.agent.data.Message
import ai.devcodex.agent.data.ToolCall
import ai.devcodex.agent.data.ToolStatus
import ai.devcodex.agent.provider.ChatTurn
import ai.devcodex.agent.provider.LlmClient
import ai.devcodex.agent.provider.ModelRegistry
import ai.devcodex.agent.provider.ProviderGateway
import ai.devcodex.agent.provider.StreamEvent
import ai.devcodex.agent.provider.streamPost
import ai.devcodex.agent.tools.BuiltinTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Agent 引擎：LLM → 工具调用 → 结果回填 → 循环，直到最终回答（Claude Code 式） */
class AgentEngine(
    private val settings: () -> AppSettings,
    private val plugins: () -> List<ai.devcodex.agent.data.PluginManifest>,
    /** 审批回调：UI 弹出卡片，用户决定后回调（挂起等待） */
    private val requestApproval: suspend (ApprovalRequest) -> Boolean,
) {
    sealed interface UiEvent {
        data class Error(val message: String) : UiEvent
        data class Finished(val sessionId: String) : UiEvent
    }

    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun ChatSession.patch(id: String, transform: (Message) -> Message): ChatSession =
        copy(messages = messages.map { if (it.id == id) transform(it) else it }, updatedAt = System.currentTimeMillis())

    /** 流式消费：处理一行 SSE，更新聚合缓冲 */
    private fun onLine(
        provider: ai.devcodex.agent.data.ProviderConfig,
        line: String,
        text: StringBuilder,
        think: StringBuilder,
        toolAgg: LinkedHashMap<Int, Triple<String, String, StringBuilder>>,
        assistantId: String,
        currentRef: ChatSession,
        onUpdate: (ChatSession) -> Unit,
        onUsage: (Long, Long, Long) -> Unit = { _, _, _ -> },
        onFirstDelta: () -> Unit = {},
    ): ChatSession {
        var current = currentRef
        ProviderGateway.parseSseLine(provider, line).forEach { ev ->
            when (ev) {
                is StreamEvent.Usage -> onUsage(ev.inputTokens, ev.outputTokens, ev.cachedTokens)
                is StreamEvent.Delta -> {
                    onFirstDelta()
                    text.append(ev.text)
                    current = current.patch(assistantId) { it.copy(content = text.toString()) }
                    onUpdate(current)
                }
                is StreamEvent.Reasoning -> {
                    think.append(ev.text)
                    current = current.patch(assistantId) { it.copy(thinking = think.toString()) }
                    onUpdate(current)
                }
                is StreamEvent.ToolCallDelta -> {
                    val ex = toolAgg[ev.index]
                    toolAgg[ev.index] = Triple(
                        ev.id ?: ex?.first ?: "call_${ev.index}",
                        ev.name ?: ex?.second ?: "",
                        StringBuilder(ex?.third ?: "").append(ev.argsFragment),
                    )
                }
                is StreamEvent.Done -> Unit
                is StreamEvent.Error -> throw RuntimeException(ev.throwable.message ?: "stream error")
            }
        }
        return current
    }

    suspend fun run(session: ChatSession, onUpdate: (ChatSession) -> Unit) {
        val s = settings()
        val provider = s.providers.find { it.id == session.providerId && it.enabled }
            ?: s.providers.firstOrNull { it.enabled && it.enabledModels.isNotEmpty() }
            ?: run { events.emit(UiEvent.Error("未配置 API 提供商，请到「提供商」页添加并拉取模型")); return }
        val model = session.modelId?.takeIf { provider.enabledModels.contains(it) }
            ?: provider.enabledModels.firstOrNull()
            ?: run { events.emit(UiEvent.Error("该提供商没有启用的模型")); return }

        // MCP 工具拉取（启用中的服务器，单服务器 8s 超时保护）
        val mcpTools = s.mcpServers.filter { it.enabled }.flatMap { cfg ->
            runCatching {
                kotlinx.coroutines.withTimeoutOrNull(8000) {
                    val sid = ai.devcodex.agent.provider.McpClient.initialize(cfg).getOrNull()
                    ai.devcodex.agent.provider.McpClient.listTools(cfg, sid).getOrDefault(emptyList())
                } ?: emptyList()
            }.getOrDefault(emptyList())
        }
        val toolDefs = BuiltinTools.all.filter { s.enabledTools.contains(it.name) } +
            ai.devcodex.agent.tools.AdvancedFsTools.all.filter { s.enabledTools.contains(it.name) } +
            customToolDefs(s.customTools) +
            mcpTools.map { mt ->
                ToolDef(
                    name = "mcp_${mt.serverId.take(8)}_${mt.name}",
                    description = "[MCP:${mt.serverName}] ${mt.description.ifBlank { mt.name }}",
                    parameters = runCatching {
                        kotlinx.serialization.json.Json.parseToJsonElement(mt.inputSchema) as? JsonObject
                    }.getOrNull() ?: buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) },
                ) { argsJson ->
                    val cfg = s.mcpServers.firstOrNull { it.id == mt.serverId }
                        ?: return@ToolDef "ERROR: MCP 服务器已移除"
                    val sid = runCatching {
                        kotlinx.coroutines.withTimeoutOrNull(8000) {
                            ai.devcodex.agent.provider.McpClient.initialize(cfg).getOrNull()
                        }
                    }.getOrNull()
                    ai.devcodex.agent.provider.McpClient.callTool(cfg, mt.name, argsJson, sid)
                }
            } +
            BuiltinTools.pluginTools(plugins())
        val toolSchemas: List<JsonObject> = toolDefs.map { t ->
            buildJsonObject {
                put("name", t.name)
                put("description", t.description)
                put("parameters", t.parameters)
            }
        }
        val basePrompt = session.systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        val skillSection = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val repo = ai.devcodex.agent.data.SkillRepo(ai.devcodex.agent.tools.BuiltinTools.appContext())
                val list = repo.list().filter { it.enabled }
                if (list.isEmpty()) "" else "\n\n已安装技能（调用 skill_read 工具获取某个技能的详细用法）：\n" +
                    list.joinToString("\n") { "- ${it.name}: ${it.description.take(120)}" }
            }.getOrDefault("")
        }
        var systemPrompt = basePrompt + skillSection
        if (session.webSearch) {
            systemPrompt += "\n\n联网搜索模式已开启：对时效性问题，先用 http_request 工具搜索（可用 https://www.bing.com/search?q=... 或 https://duckduckgo.com/html/?q=... 抓取结果页并引用来源），综合搜索结果回答。"
        }
        val effort = if (session.deepThink) "high" else s.reasoningEffort

        var current = session
        try {
            var steps = 0
            while (true) {   // 已取消最大步数限制（按用户要求），由模型自主结束循环
                steps++

                // ---- 1. 流式调 LLM ----
                var turns = buildList {
                    add(ChatTurn("system", systemPrompt))
                    current.messages.forEach { m ->
                        when (m.role) {
                            "user" -> {
                                val c = (m.llmContent ?: m.content)
                                if (c.isNotBlank()) add(ChatTurn("user", c))
                            }
                            "assistant" -> if (m.content.isNotBlank() || m.toolCalls.isNotEmpty()) add(ChatTurn("assistant", m.content, m.toolCalls))
                            "tool" -> if (m.toolCallId != null) add(ChatTurn("tool", m.toolResult ?: "(空)", toolCallId = m.toolCallId, name = m.toolName))
                        }
                    }
                }
                // 自动压缩：按会话上下文档位（覆盖或模型上限）的 60% 触发
                if (s.autoCompact) {
                    val ctxLimit = (session.contextOverride.takeIf { it > 0 }
                        ?: ai.devcodex.agent.provider.ModelRegistry.lookup(model).contextLength
                        ?: 128_000)
                    val estTokens = turns.sumOf { (it.content.length / 2.5 + it.toolCalls.size * 50).toLong() }
                    if (estTokens > (ctxLimit * 0.6).toLong() && turns.size > 8) {
                        val keepHead = 2
                        val keepTail = 6
                        val dropped = turns.subList(keepHead, turns.size - keepTail)
                        val summary = buildString {
                            append("（前文摘要：以下为较早对话的压缩记录）\n")
                            dropped.forEach { t ->
                                when (t.role) {
                                    "user" -> append("[用户] ").append(t.content.take(120)).append("\n")
                                    "assistant" -> append("[AI] ").append(t.content.take(150)).append("\n")
                                    "tool" -> append("[工具 ").append(t.name ?: "").append("] ").append((t.content).take(80)).append("\n")
                                }
                            }
                        }
                        turns = turns.subList(0, keepHead) +
                            listOf(ChatTurn("user", summary), ChatTurn("assistant", "（已了解前文摘要，继续）")) +
                            turns.subList(turns.size - keepTail, turns.size)
                    }
                }

                // rikkahub 式能力判定：无 TOOL 能力的已知模型不携带 tools（防 400）
                val supportsTools = ModelRegistry.supportsTools(model)
                val effectiveSchemas = if (supportsTools) toolSchemas else emptyList()

                val assistantId = java.util.UUID.randomUUID().toString()
                current = current.copy(messages = current.messages + Message(
                    id = assistantId, role = "assistant", model = model, timestamp = System.currentTimeMillis(),
                ))
                onUpdate(current)

                val text = StringBuilder()
                val think = StringBuilder()
                val toolAgg = LinkedHashMap<Int, Triple<String, String, StringBuilder>>()
                var inTok = 0L; var outTok = 0L; var cacheTok = 0L
                var firstTokMs = 0L
                val t0 = System.currentTimeMillis()

                var request = ProviderGateway.chatRequest(provider, model, systemPrompt, turns, effectiveSchemas, stream = true, reasoningEffort = effort)

                fun consume(line: String): ChatSession {
                    val t = System.currentTimeMillis()
                    if (firstTokMs == 0L && text.isNotEmpty()) firstTokMs = t - t0
                    return onLine(provider, line, text, think, toolAgg, assistantId, current, onUpdate,
                        onUsage = { i, o, c -> inTok += i; outTok += o; cacheTok += c },
                        onFirstDelta = { if (firstTokMs == 0L) firstTokMs = System.currentTimeMillis() - t0 })
                }

                try {
                    streamPost(LlmClient, request) { line -> current = consume(line) }
                } catch (e: Exception) {
                    val msg = e.message ?: e.toString()
                    if (msg.contains("HTTP 400") && effectiveSchemas.isNotEmpty()) {
                        // 未知模型 400：降级去掉 tools 重试一次
                        request = ProviderGateway.chatRequest(provider, model, systemPrompt, turns, emptyList(), stream = true, reasoningEffort = effort)
                        streamPost(LlmClient, request) { line -> current = consume(line) }
                    } else {
                        throw e
                    }
                }

                val toolCalls = toolAgg.values.filter { it.second.isNotBlank() }.map {
                    ToolCall(id = it.first, name = it.second, argsJson = it.third.toString().ifBlank { "{}" })
                }

                if (toolCalls.isEmpty()) {
                    // 最终回答
                    current = current.patch(assistantId) { it.copy(
                        content = text.toString().ifBlank { "(空回复)" },
                        inputTokens = inTok, outputTokens = outTok, cachedTokens = cacheTok,
                        firstTokenMs = firstTokMs, totalMs = System.currentTimeMillis() - t0,
                    ) }
                    onUpdate(current)
                    events.emit(UiEvent.Finished(current.id))
                    return
                }

                current = current.patch(assistantId) { it.copy(
                    content = text.toString(), toolCalls = toolCalls,
                    inputTokens = inTok, outputTokens = outTok, cachedTokens = cacheTok,
                    firstTokenMs = firstTokMs, totalMs = System.currentTimeMillis() - t0,
                ) }
                onUpdate(current)

                // ---- 2. 执行工具 ----
                for (tc in toolCalls) {
                    val def = toolDefs.find { it.name == tc.name }
                    val toolMsgId = java.util.UUID.randomUUID().toString()
                    if (def == null) {
                        current = current.copy(messages = current.messages + Message(
                            id = toolMsgId, role = "tool", toolCallId = tc.id, toolName = tc.name,
                            toolResult = "ERROR: 未知工具 ${tc.name}", toolStatus = ToolStatus.ERROR,
                            timestamp = System.currentTimeMillis(),
                        ))
                        onUpdate(current)
                        continue
                    }
                    // 审批判定：用户已"总是允许"的直接放行；否则高危必审、fs_write 沙箱外必审
                    val alwaysAllowed = s.toolPerms[tc.name] == "ALLOW"
                    val needApproval = !alwaysAllowed && (
                        def.risk == ToolDef.Risk.HIGH ||
                            (tc.name == "fs_write" && !tc.argsJson.contains(SANDBOX_MARK)))
                    if (needApproval) {
                        val reason = when (def.risk) {
                            ToolDef.Risk.HIGH -> "高危操作，需确认：${def.description.take(50)}"
                            else -> "沙箱外写入，需确认"
                        }
                        val granted = requestApproval(ApprovalRequest(tc, reason) { true })
                        if (!granted) {
                            current = current.copy(messages = current.messages + Message(
                                id = toolMsgId, role = "tool", toolCallId = tc.id, toolName = tc.name,
                                toolResult = "用户拒绝执行该操作", toolStatus = ToolStatus.DENIED,
                                timestamp = System.currentTimeMillis(),
                            ))
                            onUpdate(current)
                            continue
                        }
                    }
                    // RUNNING → 执行 → DONE
                    current = current.copy(messages = current.messages + Message(
                        id = toolMsgId, role = "tool", toolCallId = tc.id, toolName = tc.name,
                        toolStatus = ToolStatus.RUNNING, timestamp = System.currentTimeMillis(),
                    ))
                    onUpdate(current)
                    val result = runCatching { def.execute(tc.argsJson) }
                        .getOrElse { "ERROR: ${it.message}" }
                    current = current.patch(toolMsgId) {
                        it.copy(toolResult = result, toolStatus = if (result.startsWith("ERROR")) ToolStatus.ERROR else ToolStatus.DONE)
                    }
                    onUpdate(current)
                }
                // ---- 3. 循环：工具结果已在消息里，下一轮带给 LLM ----
            }
            events.emit(UiEvent.Error("执行已中止"))
        } catch (e: CancellationException) {
            current = current.copy(messages = current.messages.map { m ->
                if (m.toolStatus == ToolStatus.RUNNING) m.copy(toolStatus = ToolStatus.ERROR, toolResult = "已中断") else m
            })
            onUpdate(current)
        } catch (e: Exception) {
            events.emit(UiEvent.Error(e.message ?: e.toString()))
            onUpdate(current)
        }
    }

    companion object {
        private const val SANDBOX_MARK = "/data/user/0/ai.devcodex.agent"
    }
}

/** 自定义工具（用户 shell 模板）→ ToolDef */
private fun customToolDefs(defs: List<ai.devcodex.agent.data.CustomToolDef>): List<ToolDef> =
    defs.filter { it.enabled }.map { ct ->
        val schema = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(ct.parameters) as? JsonObject
        }.getOrNull() ?: kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("object"))
            put("properties", kotlinx.serialization.json.buildJsonObject {})
        }
        ToolDef(
            name = ct.name,
            description = ct.description.ifBlank { "自定义工具 ${ct.name}（shell 模板）" },
            parameters = schema,
            risk = ToolDef.Risk.MEDIUM,
        ) { argsJson ->
            val cmd = ct.command.replace(Regex("\\{\\{(\\w+)\\}\\}")) { m ->
                ai.devcodex.agent.util.JsonArg.string(argsJson, m.groupValues[1]) ?: ""
            }
            ai.devcodex.agent.shell.ShellAccess.exec(cmd).take(15000)
        }
    }

val DEFAULT_SYSTEM_PROMPT = """你是 DevCodex，一个运行在 Android 手机上的 AI 智能体（Agent）。
你可以通过工具读写手机本地文件、执行 shell 命令、发 HTTP 请求、读取屏幕内容、做界面自动化。

工作区：用户上传的文件都在工作区，工具用相对路径即可访问（如 fs_read "报告.md"）。
用户消息中出现 [附件 文件名] 标记时，表示该工作区文件已随消息附上内容，直接使用即可，不必重复读取。
在非明确要求时，产出文件也写到工作区（相对路径），方便用户在「工作区」页查看。

行为准则：
1. 需要信息时主动调用工具获取，不要凭空编造手机上的内容。
2. 文件操作给出明确路径；危险操作前先说明意图。
3. 工具返回 ERROR 时分析原因，换路径或方案重试，同一方案不要重试超过 2 次。
4. 回答简洁、直接、面向行动。用中文交流。
5. 超出工具能力的请求如实说明，不要假装已执行。"""
