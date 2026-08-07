package ai.devcodex.agents.data.remote

import ai.devcodex.agents.data.model.ChatMessage
import ai.devcodex.agents.data.model.MessageRole
import ai.devcodex.agents.data.model.SettingsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AnthropicProvider(private val settings: SettingsData) : AiProvider {

    override val displayName = "Anthropic Claude"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun base() = settings.baseUrl.trimEnd('/')

    override suspend fun streamChat(
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val systemText = messages.filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n") { it.content }

        val body = JSONObject().apply {
            put("model", settings.model)
            put("max_tokens", 4096)
            put("stream", true)
            put("temperature", settings.temperature.toDouble())
            if (systemText.isNotBlank()) put("system", systemText)
            put("messages", JSONArray().apply {
                messages.filter { it.role != MessageRole.SYSTEM }.forEach { m ->
                    put(
                        JSONObject().apply {
                            put("role", if (m.role == MessageRole.USER) "user" else "assistant")
                            put("content", m.content)
                        }
                    )
                }
            })
        }

        val request = Request.Builder()
            .url("${base()}/v1/messages")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .apply {
                header("x-api-key", settings.apiKey)
                header("anthropic-version", "2023-06-01")
                header("Accept", "text/event-stream")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw IOException("HTTP ${response.code}: ${extractError(err)}")
            }
            val source = response.body?.source()
                ?: throw IOException("响应为空")
            var received = false
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                when (event.optString("type")) {
                    "content_block_delta" -> {
                        val delta = event.optJSONObject("delta")
                        if (delta?.optString("type") == "text_delta") {
                            val text = delta.optString("text", "")
                            if (text.isNotEmpty()) {
                                received = true
                                onDelta(text)
                            }
                        }
                    }
                    "error" -> {
                        val msg = event.optJSONObject("error")?.optString("message")
                            ?: event.optString("error")
                        throw IOException("模型错误：$msg")
                    }
                    "message_stop" -> return@withContext
                }
            }
            if (!received) throw IOException("模型未返回任何内容")
        }
    }

    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/v1/models")
            .get()
            .header("x-api-key", settings.apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
            }
            val text = response.body?.string().orEmpty()
            val arr = JSONObject(text).optJSONArray("data")
            val names = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it)?.optString("id", null) }
            if (names.isEmpty()) "连接成功（未返回模型列表）" else "连接成功，可用模型：${names.take(8).joinToString(", ")}"
        }
    }

    private fun extractError(raw: String): String = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(300)
    }.getOrDefault(raw.take(300))

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}