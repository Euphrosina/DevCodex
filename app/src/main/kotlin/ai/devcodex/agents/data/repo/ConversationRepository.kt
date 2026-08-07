package ai.devcodex.agents.data.repo

import ai.devcodex.agents.data.model.ChatMessage
import ai.devcodex.agents.data.model.Conversation
import ai.devcodex.agents.data.model.MessageRole
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ConversationRepository(context: Context) {

    private val dir = File(context.filesDir, "conversations").apply { mkdirs() }

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun loadAll(): List<Conversation> = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { file -> runCatching { parse(file.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
    }

    suspend fun save(conversation: Conversation) = withContext(Dispatchers.IO) {
        val file = File(dir, "${conversation.id}.json")
        file.writeText(toJson(conversation))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }

    private fun toJson(c: Conversation): String {
        val obj = JSONObject().apply {
            put("id", c.id)
            put("title", c.title)
            put("createdAt", c.createdAt)
            put("updatedAt", c.updatedAt)
            put(
                "messages",
                JSONArray().apply {
                    c.messages.forEach { m ->
                        put(
                            JSONObject().apply {
                                put("role", m.role.name)
                                put("content", m.content)
                                put("timestamp", m.timestamp)
                                put("isError", m.isError)
                            }
                        )
                    }
                }
            )
        }
        return obj.toString()
    }

    private fun parse(text: String): Conversation {
        val obj = JSONObject(text)
        val messages = obj.optJSONArray("messages") ?: JSONArray()
        return Conversation(
            id = obj.getString("id"),
            title = obj.optString("title", "新会话"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            messages = (0 until messages.length()).map { i ->
                val m = messages.optJSONObject(i)
                ChatMessage(
                    role = MessageRole.byName(m.optString("role")),
                    content = m.optString("content", ""),
                    timestamp = m.optLong("timestamp", System.currentTimeMillis()),
                    isError = m.optBoolean("isError", false)
                )
            }
        )
    }
}