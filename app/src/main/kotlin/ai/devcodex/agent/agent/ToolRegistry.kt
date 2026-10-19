package ai.devcodex.agent.agent

import kotlinx.serialization.json.JsonObject

/** 工具定义（给 LLM 的 schema + 本地执行体） */
class ToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val risk: Risk = Risk.LOW,
    val execute: suspend (argsJson: String) -> String,
) {
    enum class Risk { LOW, MEDIUM, HIGH }   // HIGH=每次需用户确认
}
