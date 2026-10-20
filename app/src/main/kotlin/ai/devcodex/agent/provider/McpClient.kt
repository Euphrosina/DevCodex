package ai.devcodex.agent.provider

import ai.devcodex.agent.util.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** MCP 客户端（Streamable HTTP，2025-03-26 协议；兼容 SSE 响应） */
object McpClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @kotlinx.serialization.Serializable
    data class McpServerConfig(
        val id: String,
        val name: String,
        val url: String,
        val enabled: Boolean = true,
    )

    data class McpToolInfo(
        val serverId: String,
        val serverName: String,
        val name: String,
        val description: String,
        val inputSchema: String,   // JSON Schema 字符串
    )

    private var sessionInitialized = mutableSetOf<String>()

    private fun post(url: String, body: JsonObject, extraHeaders: Map<String, String> = emptyMap()): Pair<Int, String> =
        run {
            val req = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/event-stream")
                .apply { extraHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            TestClient.newCall(req).execute().use { resp ->
                resp.code to (resp.body?.string() ?: "")
            }
    }

    /** 从响应提取 JSON（兼容纯 JSON 与 SSE data: 行） */
    private fun extractJson(raw: String): JsonObject? {
        raw.lineSequence().forEach { line ->
            val l = line.removePrefix("data:").trim()
            if (l.startsWith("{")) {
                runCatching { json.parseToJsonElement(l) as? JsonObject }.getOrNull()?.let { return it }
            }
        }
        return runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    }

    /** 初始化会话（幂等），返回 sessionId（若有） */
    suspend fun initialize(cfg: McpServerConfig): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("jsonrpc", "2.0"); put("id", 1); put("method", "initialize")
                put("params", buildJsonObject {
                    put("protocolVersion", "2025-03-26")
                    put("capabilities", buildJsonObject {})
                    put("clientInfo", buildJsonObject { put("name", "DevCodex"); put("version", "3.7.0") })
                })
            }
            val (code, respBody) = post(cfg.url, body)
            if (code !in 200..299) throw RuntimeException("initialize HTTP $code: ${respBody.take(200)}")
            val resp = extractJson(respBody) ?: throw RuntimeException("响应无 JSON")
            if (resp["error"] != null) throw RuntimeException("initialize 错误: ${resp["error"]}")
            val sid = (resp["result"] as? JsonObject)
                ?.get("sessionId") as? kotlinx.serialization.json.JsonPrimitive
            sid?.contentOrNull
        }
    }

    /** 拉取工具列表 */
    suspend fun listTools(cfg: McpServerConfig, sessionId: String? = null): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val headers = sessionId?.let { mapOf("Mcp-Session-Id" to it) } ?: emptyMap()
            val body = buildJsonObject {
                put("jsonrpc", "2.0"); put("id", 2); put("method", "tools/list")
            }
            val (code, respBody) = post(cfg.url, body, headers)
            if (code !in 200..299) throw RuntimeException("tools/list HTTP $code: ${respBody.take(200)}")
            val resp = extractJson(respBody) ?: throw RuntimeException("响应无 JSON")
            if (resp["error"] != null) throw RuntimeException("tools/list 错误: ${resp["error"]}")
            val tools = (resp["result"] as? JsonObject)?.get("tools") as? kotlinx.serialization.json.JsonArray
                ?: return@runCatching emptyList<McpToolInfo>()
            tools.mapNotNull { t ->
                val obj = t as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val desc = (obj["description"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
                val schema = obj["inputSchema"]?.toString() ?: "{}"
                McpToolInfo(cfg.id, cfg.name, name, desc, schema)
            }
        }
    }

    /** 调用工具 */
    suspend fun callTool(cfg: McpServerConfig, toolName: String, argsJson: String, sessionId: String? = null): String = withContext(Dispatchers.IO) {
        val headers = sessionId?.let { mapOf("Mcp-Session-Id" to it) } ?: emptyMap()
        val params = buildJsonObject {
            put("name", toolName)
            put("arguments", runCatching { json.parseToJsonElement(argsJson) }.getOrDefault(buildJsonObject {}))
        }
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", 3); put("method", "tools/call"); put("params", params)
        }
        val (code, respBody) = post(cfg.url, body, headers)
        if (code !in 200..299) return@withContext "ERROR: HTTP $code ${respBody.take(300)}"
        val resp = extractJson(respBody) ?: return@withContext "ERROR: 响应无 JSON"
        (resp["error"] as? JsonObject)?.let { return@withContext "ERROR: ${it}" }
        val result = resp["result"] as? JsonObject ?: return@withContext respBody.take(2000)
        // content 数组取 text
        val content = result["content"] as? kotlinx.serialization.json.JsonArray
        val texts = content?.mapNotNull { c ->
            ((c as? JsonObject)?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        } ?: emptyList<String>()
        when {
            texts.isNotEmpty() -> texts.joinToString("\n").take(10000)
            result["isError"].toString().contains("true") -> "ERROR: ${result.toString().take(500)}"
            else -> result.toString().take(5000)
        }
    }
}
