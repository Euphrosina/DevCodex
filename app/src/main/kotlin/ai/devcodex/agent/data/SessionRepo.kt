package ai.devcodex.agent.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 会话持久化：filesDir/sessions/<id>.json，一个会话一个文件 */
class SessionRepo(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val dir: File get() = File(context.filesDir, "sessions").apply { mkdirs() }

    fun list(): List<ChatSession> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<ChatSession>(it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun get(id: String): ChatSession? =
        File(dir, "$id.json").takeIf { it.exists() }?.let {
            runCatching { json.decodeFromString<ChatSession>(it.readText()) }.getOrNull()
        }

    fun save(session: ChatSession) {
        File(dir, "${session.id}.json").writeText(json.encodeToString(ChatSession.serializer(), session))
    }

    fun delete(id: String) { File(dir, "$id.json").delete() }

    fun newSession(): ChatSession {
        val s = ChatSession(id = UUID.randomUUID().toString())
        save(s)
        return s
    }
}
