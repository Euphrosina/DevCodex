package ai.devcodex.agents.data.remote

import ai.devcodex.agents.data.model.ChatMessage
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

class OllamaProvider(private val settings: SettingsData) : AiProvider {

    override val displayName = "Ollama 本地"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun base() = settings.baseUrl.trimEnd('/')

    override suspend fun streamChat(
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", settings.model)
            put("stream", true)
            put(
                "options",
                JSONObject().put("temperature", settings.temperature.toDouble())
            )
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

        val request = Request.Builder()
            .url("${base()}/api/chat")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/x-ndjson")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw IOException("HTTP ${response.code}: ${err.take(500)}")
            }
            val source = response.body?.source()
                ?: throw IOException("响应为空")
            var received = false
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (obj.optBoolean("done")) break
                val text = obj.optJSONObject("message")?.optString("content", "") ?: ""
                if (text.isNotEmpty()) {
                    received = true
                    onDelta(text)
                }
            }
            if (!received) throw IOException("模型未返回任何内容")
        }
    }

    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/api/tags")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
            }
            val text = response.body?.string().orEmpty()
            val arr = JSONObject(text).optJSONArray("models")
            val names = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it)?.optString("name", null) }
            if (names.isEmpty()) "连接成功（未检出模型）" else "连接成功，已装模型：${names.take(8).joinToString(", ")}"
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}