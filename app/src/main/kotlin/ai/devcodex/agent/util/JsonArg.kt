package ai.devcodex.agent.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 从工具调用参数 JSON 里安全取值 */
object JsonArg {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(args: String): JsonElement? = runCatching { json.parseToJsonElement(args) }.getOrNull()

    fun string(args: String, key: String): String? {
        val el = parse(args) ?: return null
        return (el as? kotlinx.serialization.json.JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull
    }

    fun int(args: String, key: String): Int? =
        parse(args)?.let { (it as? kotlinx.serialization.json.JsonObject)?.get(key)?.jsonPrimitive?.intOrNull }

    fun bool(args: String, key: String): Boolean? =
        parse(args)?.let { (it as? kotlinx.serialization.json.JsonObject)?.get(key)?.jsonPrimitive?.booleanOrNull }
}
