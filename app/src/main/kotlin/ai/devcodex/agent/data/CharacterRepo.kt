package ai.devcodex.agent.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 角色卡（Operit/SillyTavern V2 兼容） */
@Serializable
data class CharacterCard(
    val name: String = "",
    val description: String = "",      // 角色设定（人格核心）
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",         // 开场白
    val mesExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "v2",
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

    fun delete(name: String): Boolean = runCatching {
        val safe = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        File(dir, "$safe.json").delete()
    }.getOrDefault(false)

    /** 导入 SillyTavern V2/V1 JSON（data 包装或平铺两种格式） */
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
