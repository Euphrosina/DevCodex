package ai.devcodex.agent.agent

import ai.devcodex.agent.data.ToolCall

/** 工具执行前的权限审批 */
data class ApprovalRequest(
    val toolCall: ToolCall,
    val reason: String,
    val respond: (Boolean) -> Unit,   // true=放行
)
