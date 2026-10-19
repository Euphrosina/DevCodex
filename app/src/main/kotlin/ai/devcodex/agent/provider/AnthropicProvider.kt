package ai.devcodex.agent.provider

import ai.devcodex.agent.data.ModelTestResult
import ai.devcodex.agent.data.ProviderConfig
import ai.devcodex.agent.util.describe
import kotlinx.serialization.json.*
import okhttp3.Request

/** Anthropic Messages API */
object AnthropicProvider {

    private fun turnsToBlocks(turns: List<ChatTurn>): JsonArray = buildJsonArray {
        turns.filter { it.role != "system" }.forEach { t ->
            add(buildJsonObject {
                put("role", if (t.role == "assistant") "assistant" else "user")
                when {
                    t.role == "assistant" && t.toolCalls.isNotEmpty() -> {
                        put("content", buildJsonArray {
                            if (t.content.isNotBlank()) add(buildJsonObject { put("type", "text"); put("text", t.content) })
                            t.toolCalls.forEach { tc ->
                                add(buildJsonObject {
                                    put("type", "tool_use")
                                    put("id", tc.id)
                                    put("name", tc.name)
                                    put("input", runCatching { LlmJson.parseToJsonElement(tc.argsJson) }.getOrDefault(buildJsonObject {}))
                                })
                            }
                        })
                    }
                    t.role == "tool" -> {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", t.toolCallId ?: "")
                                put("content", t.content.take(4000))
                            })
                        })
                    }
                    else -> put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", t.content) }) })
                }
            })
        }
    }

    fun chatRequest(p: ProviderConfig, model: String, systemPrompt: String, turns: List<ChatTurn>, tools: List<JsonObject>, stream: Boolean): Request {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 8192)
            put("stream", stream)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
            put("messages", turnsToBlocks(turns))
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t -> add(buildJsonObject { put("name", t["name"] ?: JsonNull); put("description", t["description"] ?: JsonNull); put("input_schema", t["parameters"] ?: buildJsonObject { put("type", "object") }) }) }
                })
            }
        }
        return Request.Builder()
            .url("${p.endpoint()}/v1/messages")
            .addHeader("x-api-key", p.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody(body))
            .build()
    }

    fun parseSseLine(line: String): List<StreamEvent> {
        if (!line.startsWith("data:")) return emptyList()
        val data = line.removePrefix("data:").trim()
        val obj = runCatching { LlmJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return emptyList()
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "content_block_delta" -> {
                val delta = obj["delta"]?.jsonObject ?: return emptyList()
                when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> listOf(StreamEvent.Delta(delta["text"]?.jsonPrimitive?.contentOrNull ?: ""))
                    "thinking_delta" -> listOf(StreamEvent.Reasoning(delta["thinking"]?.jsonPrimitive?.contentOrNull ?: ""))
                    "input_json_delta" -> listOf(StreamEvent.ToolCallDelta(0, null, null, delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: ""))
                    else -> emptyList()
                }
            }
            "content_block_start" -> {
                val cb = obj["content_block"]?.jsonObject ?: return emptyList()
                if (cb["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                    listOf(StreamEvent.ToolCallDelta(0, cb["id"]?.jsonPrimitive?.contentOrNull, cb["name"]?.jsonPrimitive?.contentOrNull, ""))
                } else emptyList()
            }
            "message_stop" -> listOf(StreamEvent.Done("stop"))
            else -> emptyList()
        }
    }

    suspend fun listModels(p: ProviderConfig): List<String> {
        val req = Request.Builder()
            .url("${p.endpoint()}/v1/models?limit=200")
            .addHeader("x-api-key", p.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .get().build()
        TestClient.newCall(req).await().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: ""
            val obj = LlmJson.parseToJsonElement(body).jsonObject
            return obj["data"]?.jsonArray?.mapNotNull { m ->
                (m as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            }?.sorted() ?: emptyList()
        }
    }

    suspend fun testModel(p: ProviderConfig, model: String): ModelTestResult {
        var result = ModelTestResult()
        try {
            val t0 = System.currentTimeMillis()
            val req = chatRequest(p, model, "", listOf(ChatTurn("user", "ping")), emptyList(), stream = false)
            TestClient.newCall(req).await().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                resp.body?.string()
            }
            result = result.copy(latencyMs = System.currentTimeMillis() - t0)
        } catch (e: Exception) {
            return result.copy(error = "非流式失败: ${e.describe()}")
        }
        try {
            val req = chatRequest(p, model, "", listOf(ChatTurn("user", "say hi")), emptyList(), stream = true)
            var gotDelta = false
            streamPost(TestClient, req) { line ->
                parseSseLine(line).forEach { ev -> if (ev is StreamEvent.Delta && ev.text.isNotBlank()) gotDelta = true }
            }
            result = result.copy(streamOk = gotDelta)
            if (!gotDelta) result = result.copy(error = "流式无内容输出")
        } catch (e: Exception) {
            result = result.copy(error = "流式失败: ${e.describe()}")
        }
        return result
    }
}
