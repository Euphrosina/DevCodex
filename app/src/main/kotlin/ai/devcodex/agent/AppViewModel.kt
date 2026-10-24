package ai.devcodex.agent

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ai.devcodex.agent.agent.AgentEngine
import ai.devcodex.agent.agent.ApprovalRequest
import ai.devcodex.agent.data.AppSettings
import ai.devcodex.agent.data.ChatSession
import ai.devcodex.agent.data.PluginManifest
import ai.devcodex.agent.data.ProviderConfig
import ai.devcodex.agent.data.SessionRepo
import ai.devcodex.agent.data.WorkspaceRepo
import ai.devcodex.agent.data.ToolStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as DevCodexApp
    val settingsRepo get() = appCtx.settingsRepo
    val sessionRepo get() = appCtx.sessionRepo
    val pluginRepo get() = appCtx.pluginRepo
    val workspaceRepo get() = appCtx.workspaceRepo

    val settings = appCtx.settingsRepo.settings

    private val _plugins = MutableStateFlow<List<PluginManifest>>(emptyList())
    val plugins: StateFlow<List<PluginManifest>> = _plugins
    fun reloadPlugins() { _plugins.value = pluginRepo.list() }

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions
    fun reloadSessions() { _sessions.value = sessionRepo.list() }

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession

    var runningJob: Job? = null
    val isRunning = MutableStateFlow(false)

    val toast = MutableStateFlow<String?>(null)
    val navRequest = MutableStateFlow<String?>(null)   // "settings" 等页面跳转请求
    val settingsPageRequest = MutableStateFlow<String?>(null)  // 设置内子页跳转（stats 等）

    // 审批：挂起等待 UI 回调
    private val approvalWaiters = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val _pendingApproval = MutableStateFlow<Pair<ApprovalRequest, String>?>(null) // request + message about
    val pendingApproval: StateFlow<Pair<ApprovalRequest, String>?> = _pendingApproval

    val engine = AgentEngine(
        settings = { latestSettings },
        plugins = { _plugins.value },
        requestApproval = { req -> awaitApproval(req) },
    )

    // 最新设置快照（供非挂起上下文读取）
    private var latestSettings = AppSettings()

    init {
        viewModelScope.launch {
            settings.collect { latestSettings = it }
        }
        reloadPlugins()
        reloadSessions()
        viewModelScope.launch {
            engine.events.collect { ev ->
                when (ev) {
                    is AgentEngine.UiEvent.Error -> toast.value = ev.message
                    is AgentEngine.UiEvent.Finished -> Unit
                }
            }
        }
    }

    fun openSession(id: String?) {
        val s = id?.let { sessionRepo.get(it) } ?: sessionRepo.newSession()
        if (id == null && pendingContext > 0) {
            val withCtx = s.copy(contextOverride = pendingContext)
            sessionRepo.save(withCtx)
            pendingContext = 0
            _activeSession.value = withCtx
        } else {
            _activeSession.value = s
        }
        reloadSessions()
    }

    // 流式节流：80ms 合并 UI 更新（防 token 级全列表重组 → OOM/卡死闪退）
    @Volatile private var lastUiEmit = 0L
    private fun throttledUpdate(session: ai.devcodex.agent.data.ChatSession, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastUiEmit > 80) {
            lastUiEmit = now
            _activeSession.value = session
        }
    }

    fun send(text: String) {
        val session = _activeSession.value ?: sessionRepo.newSession().also { _activeSession.value = it }
        if (text.isBlank() || isRunning.value) return
        // @引用 → 附件内容注入（LLM 侧带文件正文，UI 展示保持原文）
        val display = text.trim()
        var llmContent: String? = null
        val mentions = Regex("@([\\w\\u4e00-\\u9fa5.\\-]+)").findAll(display).map { it.groupValues[1] }.toList()
        if (mentions.isNotEmpty()) {
            val sb = StringBuilder(display)
            for (name in mentions.distinct()) {
                val entry = workspaceRepo.list().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: continue
                if (workspaceRepo.isTextFile(entry)) {
                    sb.append("\n\n[附件 ").append(entry.name).append("]\n")
                        .append(WorkspaceRepo.textPreview(entry.file, 15000))
                } else {
                    sb.append("\n\n[附件 ").append(entry.name).append("]（二进制文件，大小 ")
                        .append(workspaceRepo.formatSize(entry.size)).append("，可用工具按相对路径访问）")
                }
            }
            llmContent = sb.toString()
        }
        val userMsg = ai.devcodex.agent.data.Message(
            id = java.util.UUID.randomUUID().toString(),
            role = "user", content = display, llmContent = llmContent,
            timestamp = System.currentTimeMillis(),
        )
        val updated = session.copy(
            messages = session.messages + userMsg,
            title = if (session.title == "新会话") display.take(20) else session.title,
            updatedAt = System.currentTimeMillis(),
        )
        _activeSession.value = updated
        sessionRepo.save(updated)
        startAgent(updated)
    }

    private fun startAgent(session: ChatSession) {
        isRunning.value = true
        runningJob = viewModelScope.launch {
            var lastSave = 0L
            try {
                engine.run(session) { s ->
                    // 会话守卫：用户已切走时不覆写当前会话（引擎后台继续跑并存盘）
                    val stillActive = _activeSession.value?.id == session.id
                    // UI 节流 80ms：防 token 级全列表重组（聊久卡死/闪退根因）
                    if (stillActive) throttledUpdate(s)
                    // 节流存盘：流式期间最多 1 次/秒，防每 token 写盘卡顿
                    val now = System.currentTimeMillis()
                    if (now - lastSave > 1000) {
                        lastSave = now
                        saveSessionBg(s)
                    }
                }
            } finally {
                isRunning.value = false
                // 最终态刷新（仅当用户未切走）
                if (_activeSession.value?.id == session.id) {
                    runCatching { sessionRepo.get(session.id) }?.getOrNull()?.let { _activeSession.value = it }
                }
                _pendingApproval.value = null
                // 结束必存最终态
                _activeSession.value?.let { saveSessionBg(it) }
            }
        }
    }

    fun stop() {
        runningJob?.cancel()
        runningJob = null
        _pendingApproval.value = null
        isRunning.value = false
        // 会话结束：清工具会话授权（下次新会话重新审批）
        engine.engineSessionAllows.clear()
    }

    private fun saveSessionBg(s: ChatSession) {
        viewModelScope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sessionRepo.save(s) } }
    }

    fun clearActiveSession() {
        val cur = _activeSession.value ?: return
        val cleared = cur.copy(messages = emptyList())
        _activeSession.value = cleared
        sessionRepo.save(cleared)
    }

    @Volatile var pendingContext: Int = 0
    val chatScrollMap = java.util.concurrent.ConcurrentHashMap<String, Int>()


    fun clearMessages() {
        val cur = _activeSession.value ?: return
        val cleared = cur.copy(messages = emptyList(), summary = null)
        _activeSession.value = cleared
        sessionRepo.save(cleared)
    }

    fun compactNow() {
        val cur = _activeSession.value ?: return
        val msgs = cur.messages
        if (msgs.size < 6) return
        val keepHead = 2
        val keepTail = 4
        val dropped = msgs.subList(keepHead, msgs.size - keepTail)
        val summary = buildString {
            append("（前文摘要：较早对话已压缩）\n")
            dropped.forEach { m ->
                when (m.role) {
                    "user" -> append("[用户] ").append(m.content.take(120)).append("\n")
                    "assistant" -> append("[AI] ").append(m.content.take(150)).append("\n")
                    "tool" -> append("[工具 ").append(m.toolName ?: "").append("] ").append((m.toolResult ?: "").take(80)).append("\n")
                }
            }
        }
        val kept = msgs.subList(0, keepHead) + msgs.subList(msgs.size - keepTail, msgs.size)
        val compacted = cur.copy(messages = kept, summary = summary)
        _activeSession.value = compacted
        sessionRepo.save(compacted)
    }

    fun exportSession() {
        val cur = _activeSession.value ?: return
        val md = buildString {
            append("# ${cur.title.ifBlank { "会话" }}\n\n")
            cur.messages.filter { it.role != "tool" }.forEach { m ->
                append(if (m.role == "user") "**我：**\n" else "**AI：**\n")
                append(m.content).append("\n\n")
            }
        }
        val ws = ai.devcodex.agent.tools.BuiltinTools.workspaceRoot()
        java.io.File(ws, "session-${cur.id.take(6)}.md").writeText(md)
    }

    fun appendLocalNotice(text: String) {
        val cur = _activeSession.value ?: return
        val withNotice = cur.copy(messages = cur.messages + ai.devcodex.agent.data.Message(
            id = java.util.UUID.randomUUID().toString(),
            role = "assistant",
            content = text,
            model = "local",
            timestamp = System.currentTimeMillis(),
        ))
        _activeSession.value = withNotice
        sessionRepo.save(withNotice)
    }

    fun setSessionContext(ctx: Int) {
        val cur = _activeSession.value
        if (cur == null) {
            // 会话未创建：挂起，新会话继承
            pendingContext = ctx
            return
        }
        val updated = cur.copy(contextOverride = ctx)
        _activeSession.value = updated
        sessionRepo.save(updated)
    }

    fun toggleSessionFlag(flag: String) {
        val cur = _activeSession.value ?: return
        val updated = when (flag) {
            "deepThink" -> cur.copy(deepThink = !cur.deepThink)
            "webSearch" -> cur.copy(webSearch = !cur.webSearch)
            else -> return
        }
        _activeSession.value = updated
        sessionRepo.save(updated)
    }

    fun updateSessionModel(providerId: String, modelId: String) {
        val cur = _activeSession.value ?: return
        val updated = cur.copy(providerId = providerId, modelId = modelId)
        _activeSession.value = updated
        sessionRepo.save(updated)
    }

    fun getPreferences(): android.content.SharedPreferences =
        getApplication<android.app.Application>().getSharedPreferences("devcodex_stats", android.content.Context.MODE_PRIVATE)

    fun deleteMessage(msgId: String) {
        val cur = _activeSession.value ?: return
        val updated = cur.copy(messages = cur.messages.filter { it.id != msgId })
        _activeSession.value = updated
        sessionRepo.save(updated)
    }

    fun deleteSession(id: String) {
        sessionRepo.delete(id)
        if (_activeSession.value?.id == id) _activeSession.value = null
        reloadSessions()
    }

    // ============ 审批 ============
    private suspend fun awaitApproval(req: ApprovalRequest): Boolean =
        suspendCancellableCoroutine { cont ->
            approvalWaiters[req.toolCall.id] = { granted ->
                cont.resume(granted)
            }
            _pendingApproval.value = req to (latestSettings.providers.find { p -> p.id == _activeSession.value?.providerId }?.name ?: "")
            cont.invokeOnCancellation { approvalWaiters.remove(req.toolCall.id); _pendingApproval.value = null }
        }

    fun respondApproval(granted: Boolean, always: Boolean = false) {
        val pending = _pendingApproval.value ?: return
        if (granted && always) {
            val toolName = pending.first.toolCall.name
            // P0 审计修复：仅本会话内记住（引擎内存集合，重启/新会话失效），不再永久写入 DataStore
            engine.engineSessionAllows.add(toolName)
        }
        approvalWaiters.remove(pending.first.toolCall.id)?.invoke(granted)
        _pendingApproval.value = null
    }

    // ============ 提供商操作 ============
    fun upsertProvider(p: ProviderConfig) {
        viewModelScope.launch {
            settingsRepo.update { s ->
                val exists = s.providers.any { it.id == p.id }
                val list = if (exists) s.providers.map { if (it.id == p.id) p else it } else s.providers + p
                // 默认提供商唯一
                val fixed = if (p.isDefault) list.map { it.copy(isDefault = it.id == p.id) } else list
                s.copy(providers = fixed)
            }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            settingsRepo.update { s -> s.copy(providers = s.providers.filter { it.id != id }) }
        }
    }

    fun setDefaultProvider(id: String) {
        viewModelScope.launch {
            settingsRepo.update { s -> s.copy(providers = s.providers.map { it.copy(isDefault = it.id == id) }) }
        }
    }

    fun saveSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(DevCodexApp.instance) as T
        }
    }
}
