package ai.devcodex.agent.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============ 提供商 ============
enum class ProviderType { OPENAI_COMPAT, ANTHROPIC, OLLAMA }

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String = "",
    val type: ProviderType = ProviderType.OPENAI_COMPAT,
    val baseUrl: String = "",
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    val enabledModels: List<String> = emptyList(),
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
) {
    fun endpoint(): String = baseUrl.trimEnd('/')
}

@Serializable
data class ModelTestResult(
    val latencyMs: Long = -1,        // 非流式首包
    val streamOk: Boolean = false,   // 流式
    val toolsOk: Boolean = false,    // 工具调用支持
    val error: String? = null,
)

// ============ 会话与消息 ============
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    @SerialName("arguments") val argsJson: String = "{}",
)

@Serializable
enum class ToolStatus { PENDING, RUNNING, AWAITING_APPROVAL, DONE, ERROR, DENIED }

@Serializable
data class Message(
    val id: String,
    val role: String,                       // user | assistant | tool | system
    val content: String = "",
    val llmContent: String? = null,         // 发给 LLM 的内容（含附件展开）
    // 消息统计（流式结束时填充）
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedTokens: Long = 0,
    val firstTokenMs: Long = 0,             // 首包耗时
    val totalMs: Long = 0,                  // 总耗时
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,         // tool 消息对应
    val toolName: String? = null,
    val toolStatus: ToolStatus = ToolStatus.DONE,
    val toolResult: String? = null,         // 工具输出（展示用）
    val thinking: String? = null,
    val model: String? = null,
    val timestamp: Long = 0,
    val pendingApproval: PendingApproval? = null,
)

@Serializable
data class PendingApproval(
    val toolCall: ToolCall,
    val reason: String,
    @Transient val deferredId: String = java.util.UUID.randomUUID().toString(),
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String = "新会话",
    val messages: List<Message> = emptyList(),
    val providerId: String? = null,
    val modelId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val systemPrompt: String? = null,
)

// ============ 设置 ============
@Serializable
data class AppSettings(
    val providers: List<ProviderConfig> = emptyList(),
    val theme: ThemePrefsData = ThemePrefsData(),
    val enabledTools: Set<String> = defaultTools(),
    val toolPerms: Map<String, String> = emptyMap(),  // 工具名 -> ALLOW（总是允许）；缺省=每次询问
    val maxAgentSteps: Int = 24,
    // MCP 服务器（Streamable HTTP）
    val mcpServers: List<ai.devcodex.agent.provider.McpClient.McpServerConfig> = emptyList(),
    // 自定义工具：name/description/command 模板（{{参数}} 替换，走 Shell 通道）
    val customTools: List<CustomToolDef> = emptyList(),
    // 思考推理等级：空=默认, low/medium/high
    val reasoningEffort: String = "",
) {
    companion object {
        fun defaultTools(): Set<String> = setOf(
            "fs_read", "fs_write", "fs_list", "fs_delete",
            "shell_exec", "http_request", "memory_save", "memory_read",
            "app_list", "device_info", "ui_snapshot", "ui_tap", "ui_back", "ui_home", "ui_scroll", "notify",
            "skill_read", "skill_list",
            "fs_copy", "fs_move", "fs_mkdir", "fs_rename", "fs_grep", "fs_find",
            "fs_chmod", "fs_tail", "fs_head", "fs_stat", "fs_wc",
        )
    }
}

@Serializable
data class ThemePrefsData(
    val presetId: String = "claude",
    val colorMode: String = "SYSTEM",    // SYSTEM LIGHT DARK
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val appFont: String = "gsf",
    val chatFont: String = "gsf",
    val navPosition: String = "bottom",    // bottom | rail（左侧）
    val immersive: Boolean = false,        // 沉浸模式：隐藏导航栏（聊天页全屏）
)

// ============ 自定义工具 ============
@Serializable
data class CustomToolDef(
    val name: String,                 // 英文名
    val description: String = "",
    val parameters: String = "{}",    // JSON Schema 字符串
    val command: String,              // shell 模板，{{参数名}} 替换
    val enabled: Boolean = true,
)

// ============ 插件 ============
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val tools: List<PluginTool> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class PluginTool(
    val name: String,                     // 如 ha_send_command（必须 plugin 工具名前缀）
    val description: String = "",
    val parameters: String = "{}",        // JSON Schema 字符串
    val endpoint: PluginEndpoint,
)

@Serializable
data class PluginEndpoint(
    val method: String = "POST",          // GET/POST/PUT/DELETE
    val url: String = "",                 // 支持 {{param}} 占位
    val headers: Map<String, String> = emptyMap(),
    val bodyTemplate: String? = null,     // JSON 模板，{{param}} 替换
    val resultPath: String? = null,       // 简单路径提取 a.b.c 或 null=全文
)
