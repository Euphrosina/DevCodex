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
        val protocol: String = "auto",        // auto | streamable | sse
        val headers: Map<String, String> = emptyMap(),   // 鉴权/自定义头（如 Authorization: Bearer xxx）
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

    // ============ SSE transport（MCP over HTTP+SSE：GET /sse 拿端点，POST 消息，响应走事件流） ============

    /** SSE 会话：连接 + endpoint + 响应读取器 */
    private class SseSession(val call: okhttp3.Call, val reader: java.io.BufferedReader) {
        var endpointId: String? = null
    }

    private val sseSessions = java.util.concurrent.ConcurrentHashMap<String, SseSession>()

    /** 建立/复用 SSE 会话，返回消息端点（绝对 URL） */
    private fun ensureSse(cfg: McpServerConfig): String {
        sseSessions[cfg.id]?.let { return it.endpointId ?: "" }
        val req = Request.Builder()
            .url(cfg.url)   // SSE 模式下 url 即 /sse 端点
            .addHeader("Accept", "text/event-stream")
            .apply { cfg.headers.forEach { (k, v) -> addHeader(k, v) } }
            .get().build()
        val call = TestClient.newCall(req)
        val resp = call.execute()
        if (!resp.isSuccessful) throw RuntimeException("SSE 连接失败 HTTP ${resp.code}")
        val source = resp.body?.source() ?: throw RuntimeException("SSE 无响应体")
        val reader = source.inputStream().bufferedReader()
        // 第一条 event 的 data = 消息端点（如 /messages?sessionId=xxx）
        val session = SseSession(call, reader)
        sseSessions[cfg.id] = session
        var line: String?
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 10_000) {
            line = reader.readLine() ?: throw RuntimeException("SSE 流关闭（未收到端点）")
            val l = line.trim()
            if (l.startsWith("data:")) {
                val endpoint = l.removePrefix("data:").trim()
                session.endpointId = endpoint
                return toAbsolute(cfg.url, endpoint)
            }
        }
        throw RuntimeException("SSE 等待端点超时")
    }

    /** SSE POST 后从事件流读含 id 的 JSON 响应 */
    private fun sseRoundTrip(cfg: McpServerConfig, endpoint: String, body: JsonObject): JsonObject {
        val headers = cfg.headers
        val req = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        TestClient.newCall(req).execute().use { resp ->
            if (resp.code !in 200..299) throw RuntimeException("SSE POST HTTP ${resp.code}")
        }
        // 响应通过事件流返回（data: 行含 JSON）
        val session = sseSessions[cfg.id] ?: throw RuntimeException("SSE 会话丢失")
        val id = (body["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 30_000) {
            val line = session.reader.readLine() ?: throw RuntimeException("SSE 流关闭（等待响应）")
            val l = line.trim()
            if (l.startsWith("data:")) {
                val payload = l.removePrefix("data:").trim()
                if (payload.startsWith("{")) {
                    val obj = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
                    if (obj != null) {
                        val respId = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                        if (id == null || respId == null || respId == id) return obj
                    }
                }
            }
        }
        throw RuntimeException("SSE 响应超时")
    }

    private fun toAbsolute(sseUrl: String, endpoint: String): String {
        if (endpoint.startsWith("http")) return endpoint
        val base = sseUrl.substringBefore("/sse").trimEnd('/')
        return base + (if (endpoint.startsWith("/")) endpoint else "/$endpoint")
    }

    private fun closeSse(cfg: McpServerConfig) {
        sseSessions.remove(cfg.id)?.let { runCatching { it.call.cancel() } }
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
            if (cfg.protocol == "sse") {
                val endpoint = ensureSse(cfg)
                val resp = sseRoundTrip(cfg, endpoint, body)
                if (resp["error"] != null) throw RuntimeException("initialize 错误: ${resp["error"]}")
                return@runCatching cfg.id
            }
            val (code, respBody) = post(cfg.url, body, cfg.headers)
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
            if (cfg.protocol == "sse") {
                val endpoint = ensureSse(cfg)
                val resp = sseRoundTrip(cfg, endpoint, body)
                if (resp["error"] != null) throw RuntimeException("tools/list 错误: ${resp["error"]}")
                return@runCatching parseTools(cfg, resp)
            }
            val (code, respBody) = post(cfg.url, body, headers + cfg.headers)
            if (code !in 200..299) throw RuntimeException("tools/list HTTP $code: ${respBody.take(200)}")
            val resp = extractJson(respBody) ?: throw RuntimeException("响应无 JSON")
            if (resp["error"] != null) throw RuntimeException("tools/list 错误: ${resp["error"]}")
            parseTools(cfg, resp)
        }
    }

    /** 解析 tools/list 响应 */
    private fun parseTools(cfg: McpServerConfig, resp: JsonObject): List<McpToolInfo> {
        val tools = (resp["result"] as? JsonObject)?.get("tools") as? kotlinx.serialization.json.JsonArray
            ?: return emptyList()
        return tools.mapNotNull { t ->
            val obj = t as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val desc = (obj["description"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
            val schema = obj["inputSchema"]?.toString() ?: "{}"
            McpToolInfo(cfg.id, cfg.name, name, desc, schema)
        }
    }

    /** 调用工具 */
    suspend fun callTool(cfg: McpServerConfig, toolName: String, argsJson: String, sessionId: String? = null): String = withContext(Dispatchers.IO) {
        if (cfg.protocol == "sse") {
            return@withContext runCatching {
                val endpoint = ensureSse(cfg)
                val params = buildJsonObject {
                    put("name", toolName)
                    put("arguments", runCatching { json.parseToJsonElement(argsJson) }.getOrDefault(buildJsonObject {}))
                }
                val body = buildJsonObject {
                    put("jsonrpc", "2.0"); put("id", 3); put("method", "tools/call"); put("params", params)
                }
                val resp = sseRoundTrip(cfg, endpoint, body)
                formatToolResult(resp)
            }.getOrElse { "ERROR(sse): ${it.message}" }
        }
        val headers = sessionId?.let { mapOf("Mcp-Session-Id" to it) } ?: emptyMap()
        val params = buildJsonObject {
            put("name", toolName)
            put("arguments", runCatching { json.parseToJsonElement(argsJson) }.getOrDefault(buildJsonObject {}))
        }
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", 3); put("method", "tools/call"); put("params", params)
        }
        val (code, respBody) = post(cfg.url, body, headers + cfg.headers)
        if (code !in 200..299) return@withContext "ERROR: HTTP $code ${respBody.take(300)}"
        val resp = extractJson(respBody) ?: return@withContext "ERROR: 响应无 JSON"
        formatToolResult(resp, fallback = respBody.take(2000))
    }

    /** tools/call 响应 → 文本 */
    private fun formatToolResult(resp: JsonObject, fallback: String = ""): String {
        (resp["error"] as? JsonObject)?.let { return "ERROR: ${it}" }
        val result = resp["result"] as? JsonObject ?: return fallback
        val content = result["content"] as? kotlinx.serialization.json.JsonArray
        val texts = content?.mapNotNull { c ->
            ((c as? JsonObject)?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        } ?: emptyList<String>()
        return when {
            texts.isNotEmpty() -> texts.joinToString("\n").take(10000)
            result["isError"].toString().contains("true") -> "ERROR: ${result.toString().take(500)}"
            else -> result.toString().take(5000)
        }
    }
}
