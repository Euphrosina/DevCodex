package ai.devcodex.agent.agent

import kotlinx.serialization.json.JsonObject

/** 工具定义（给 LLM 的 schema + 本地执行体） */
class ToolDef(
    val name: String,
    description: String,
    val parameters: JsonObject,
    val risk: Risk = Risk.LOW,
    val descriptionRes: Int? = null,
    val execute: suspend (argsJson: String) -> String,
) {
    private val staticDescription: String = description

    /** 描述按当前语言实时解析；未提供资源时回退静态文案。
     *  不能在对象初始化阶段解析，否则 BuiltinTools.appContext 尚未赋值。 */
    val description: String
        get() = descriptionRes?.let {
            ai.devcodex.agent.i18n.AppLocale.string(it)
        }?.takeIf { it.isNotEmpty() } ?: staticDescription

    enum class Risk { LOW, MEDIUM, HIGH }   // HIGH=每次需用户确认
}
