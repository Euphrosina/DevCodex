package ai.devcodex.agent.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** 插件持久化：filesDir/plugins/<id>.json */
class PluginRepo(private val context: Context) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir: File get() = File(context.filesDir, "plugins").apply { mkdirs() }

    fun list(): List<PluginManifest> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<PluginManifest>(it.readText()) }.getOrNull() }
            ?: emptyList()

    fun save(m: PluginManifest) { File(dir, "${m.id}.json").writeText(json.encodeToString(PluginManifest.serializer(), m)) }
    fun delete(id: String) { File(dir, "$id.json").delete() }

    fun installFromText(text: String): Result<PluginManifest> = runCatching {
        val m = json.decodeFromString(PluginManifest.serializer(), text.trim())
        require(m.id.isNotBlank() && m.name.isNotBlank()) { "插件缺少 id 或 name" }
        require(m.tools.isNotEmpty()) { "插件至少要声明一个工具" }
        require(m.tools.all { it.name.isNotBlank() && it.endpoint.url.isNotBlank() }) { "工具缺少 name 或 url" }
        require(m.tools.none { it.name.contains(" ") }) { "工具名不能含空格" }
        save(m)
        m
    }
}
