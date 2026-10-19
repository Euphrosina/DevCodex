package ai.devcodex.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.devcodex.agent.data.Message
import ai.devcodex.agent.data.ToolStatus

private val Mono = FontFamily.Monospace

// ============ 用户消息：rikkahub 式右侧主色气泡 ============
@Composable
fun UserBubble(msg: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            // rikkahub 风格：右下角小圆角形成指向
            shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            modifier = Modifier.widthIn(max = 310.dp),
        ) {
            Text(
                msg.content,
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// ============ AI 消息：全宽 + 头像 + 流式光标（rikkahub 布局） ============
@Composable
fun AssistantMessage(msg: Message, showAvatar: Boolean = true) {
    Row(Modifier.fillMaxWidth()) {
        if (showAvatar) {
            // AI 头像：圆角方块 + 闪电符号
            Box(
                Modifier
                    .padding(end = 10.dp, top = 2.dp)
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚡", style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f)) {
            if (!msg.thinking.isNullOrBlank()) ThinkingBlock(msg.thinking!!)
            if (msg.content.isBlank() && msg.toolCalls.isEmpty() && msg.thinking.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape))
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape))
                    Text("思考中…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (msg.content.isNotBlank()) {
                Text(
                    msg.content + if (msg.toolStatus == ToolStatus.RUNNING) " ▍" else "",
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

// ============ 思考块（rikkahub 式细边框折叠） ============
@Composable
fun ThinkingBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(bottom = 6.dp),
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✻", color = MaterialTheme.colorScheme.tertiary)
                Text(
                    if (expanded) "思考过程" else "已深度思考",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
                    Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============ 工具卡片（rikkahub 式：图标 + 名称 + 状态 + 参数摘要） ============
@Composable
fun ToolCard(msg: Message) {
    var expanded by remember { mutableStateOf(false) }
    val status = msg.toolStatus
    val result = msg.toolResult
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ToolStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                ToolStatus.DENIED, ToolStatus.AWAITING_APPROVAL -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(vertical = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Icon(
                    Icons.Filled.Terminal, null,
                    Modifier.size(16.dp),
                    tint = when (status) {
                        ToolStatus.ERROR -> MaterialTheme.colorScheme.error
                        ToolStatus.DENIED -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    msg.toolName ?: "tool",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                )
                when (status) {
                    ToolStatus.RUNNING, ToolStatus.PENDING ->
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp)
                    ToolStatus.DONE -> Icon(Icons.Filled.CheckCircle, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    ToolStatus.ERROR -> Icon(Icons.Filled.ErrorOutline, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                    else -> Icon(Icons.Filled.Close, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded) Icon(Icons.Filled.ExpandLess, null, Modifier.size(14.dp))
                else Icon(Icons.Filled.ExpandMore, null, Modifier.size(14.dp))
            }
            // 未展开时结果首行摘要
            if (!expanded && !result.isNullOrBlank()) {
                Text(
                    result.lineSequence().firstOrNull { it.isNotBlank() }?.take(56)?.plus("…") ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = Mono,
                    color = if (status == ToolStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (expanded) {
                result?.let {
                    Text(
                        it.take(3000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        color = if (status == ToolStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ============ 审批卡片 ============
@Composable
fun ApprovalCard(
    reason: String,
    toolName: String,
    onAllow: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("⚠ 权限确认 · $toolName", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.padding(3.dp))
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.padding(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDeny) { Text("拒绝") }
                TextButton(onClick = onAlwaysAllow) { Text("总是允许") }
                FilledIconButton(onClick = onAllow, modifier = Modifier.heightIn(min = 36.dp)) {
                    Icon(Icons.Filled.PlayArrow, "允许一次")
                }
            }
        }
    }
}

// ============ 输入栏（rikkahub 式：大圆角顶部容器 + 模型 chip + 圆形发送） ============
@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    running: Boolean,
    modelLabel: String?,
    onPickModel: () -> Unit,
    onAttach: () -> Unit = {},
) {
    // rikkahub 视觉：顶部大圆角浮层容器
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().imePadding(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 模型选择 chip 行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickModel() }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        modelLabel ?: "＋ 选择模型",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (running) {
                    Spacer(Modifier.width(8.dp))
                    Text("● 运行中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("给 DevCodex 下指令…") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Filled.AttachFile, "上传附件",
                            Modifier.clickable { onAttach() },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { if (running) onStop() else onSend() },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                ) {
                    if (running) Icon(Icons.Filled.Stop, "停止")
                    else Icon(Icons.AutoMirrored.Filled.Send, "发送")
                }
            }
        }
    }
}
