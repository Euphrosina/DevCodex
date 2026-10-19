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
) {
    companion object {
        fun defaultTools(): Set<String> = setOf(
            "fs_read", "fs_write", "fs_list", "fs_delete",
            "shell_exec", "http_request", "memory_save", "memory_read",
            "app_list", "device_info", "ui_snapshot", "ui_tap", "ui_back", "ui_home", "ui_scroll", "notify",
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
