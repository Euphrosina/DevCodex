package ai.devcodex.agents.data.remote

import ai.devcodex.agents.data.model.ChatMessage
import ai.devcodex.agents.data.model.SettingsData
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(private val settings: SettingsData) : AiProvider {

    override val displayName = "OpenAI 兼容"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun base() = settings.baseUrl.trimEnd('/')

    private fun buildRequest(messages: List<ChatMessage>): Request {
        val body = JSONObject().apply {
            put("model", settings.model)
            put("stream", true)
            put("temperature", settings.temperature.toDouble())
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(
                        JSONObject().apply {
                            put("role", m.role.name.lowercase())
                            put("content", m.content)
                        }
                    )
                }
            })
        }
        return Request.Builder()
            .url("${base()}/chat/completions")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .apply {
                if (settings.apiKey.isNotBlank()) header("Authorization", "Bearer ${settings.apiKey}")
            }
            .build()
    }

    private fun String?.optDelta(): String? {
        if (this == null) return null
        val obj = runCatching { JSONObject(this) }.getOrNull() ?: return null
        if (obj.has("error")) {
            val msg = obj.optJSONObject("error")?.optString("message") ?: obj.optString("error")
            throw IOException("模型错误：$msg")
        }
        return obj.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content", null)
    }

    override suspend fun streamChat(
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = buildRequest(messages)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IOException("HTTP ${response.code}: ${body.take(500)}")
            }
            val source = response.body?.source()
            if (source == null) {
                throw IOException("响应为空")
            }
            var received = false
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val delta = data.optDelta() ?: continue
                if (delta.isNotEmpty()) {
                    received = true
                    onDelta(delta)
                }
            }
            if (!received) throw IOException("模型未返回任何内容")
        }
    }

    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/models")
            .get()
            .apply {
                if (settings.apiKey.isNotBlank()) header("Authorization", "Bearer ${settings.apiKey}")
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
            }
            val text = response.body?.string().orEmpty()
            val arr = JSONObject(text).optJSONArray("data")
            val names = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it)?.optString("id", null) }
            if (names.isEmpty()) "连接成功（未返回模型列表）" else "连接成功，可用模型：${names.take(8).joinToString(", ") }" +
                if (names.size > 8) " 等 ${names.size} 个" else ""
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}