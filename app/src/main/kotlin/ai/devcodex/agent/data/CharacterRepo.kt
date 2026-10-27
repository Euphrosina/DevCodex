package ai.devcodex.agent.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 角色卡（Operit/SillyTavern V2 兼容） */
@Serializable
data class CharacterCard(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "v2",
    val avatar: String = "",
    val characterBook: CharacterBook? = null,   // 世界书（lorebook）
)

/** 世界书（SillyTavern V2 character_book） */
@Serializable
data class CharacterBook(
    val name: String = "",
    val entries: List<LoreEntry> = emptyList(),
)

@Serializable
data class LoreEntry(
    val keys: List<String> = emptyList(),   // 触发关键词
    val content: String = "",               // 注入内容
    val enabled: Boolean = true,
    val insertionOrder: Int = 0,
)

object CharacterRepo {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private lateinit var dir: File

    fun init(ctx: Context) { dir = File(ctx.filesDir, "characters").apply { mkdirs() } }

    fun list(): List<CharacterCard> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<CharacterCard>(it.readText()) }.getOrNull() }
            ?: emptyList()

    fun save(c: CharacterCard): Boolean = runCatching {
        val safe = c.name.ifBlank { "未命名" }.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        File(dir, "$safe.json").writeText(json.encodeToString(CharacterCard.serializer(), c))
        true
    }.getOrDefault(false)

    fun importAvatar(name: String, src: File): String? = runCatching {
        val ext = if (src.extension.isBlank()) "png" else src.extension
        val dest = File(dir, "avatar_" + name.replace(Regex("[/\\\\:*?\"<>|]"), "_") + "." + ext)
        src.copyTo(dest, overwrite = true)
        dest.name
    }.getOrNull()

    fun avatarFile(c: CharacterCard): File? = c.avatar.takeIf { it.isNotBlank() }?.let { File(dir, it).takeIf(File::exists) }

    fun delete(name: String): Boolean = runCatching {
        val safe = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        File(dir, "$safe.json").delete()
    }.getOrDefault(false)

    private fun parseBook(el: kotlinx.serialization.json.JsonElement?): CharacterBook? = runCatching {
        val obj = el as? kotlinx.serialization.json.JsonObject ?: return null
        val entries = (obj["entries"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { e ->
                val eo = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                LoreEntry(
                    keys = (eo["keys"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList(),
                    content = (eo["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
                    enabled = (eo["enabled"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean() ?: true,
                )
            } ?: emptyList()
        CharacterBook(name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "", entries = entries)
    }.getOrNull()

    fun importFromJson(text: String): CharacterCard? = runCatching {
        val root = json.parseToJsonElement(text).let { it as? kotlinx.serialization.json.JsonObject } ?: return null
        val data = (root["data"] as? kotlinx.serialization.json.JsonObject)
        fun str(obj: kotlinx.serialization.json.JsonObject?, k: String): String =
            (obj?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
        if (data != null) {
            CharacterCard(
                name = str(data, "name"),
                description = str(data, "description"),
                personality = str(data, "personality"),
                scenario = str(data, "scenario"),
                firstMes = str(data, "first_mes"),
                mesExample = str(data, "mes_example"),
                creatorNotes = str(data, "creator_notes"),
                systemPrompt = str(data, "system_prompt"),
                postHistoryInstructions = str(data, "post_history_instructions"),
                alternateGreetings = (data["alternate_greetings"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList(),
                tags = (data["tags"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList(),
                creator = str(data, "creator"),
                characterBook = parseBook(data["character_book"]),
            )
        } else {
            CharacterCard(
                name = str(root, "name"),
                description = str(root, "description"),
                personality = str(root, "personality"),
                scenario = str(root, "scenario"),
                firstMes = str(root, "first_mes"),
                mesExample = str(root, "mes_example"),
            )
        }
    }.getOrNull()
}
