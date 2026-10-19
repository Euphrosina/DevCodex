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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.devcodex.agent.data.Message
import ai.devcodex.agent.data.ToolStatus
import ai.devcodex.agent.theme.FlexFonts

private val Mono = FontFamily.Monospace

// ============ 用户消息：终端风全宽 "❯ " 行 ============
@Composable
fun UserLine(msg: Message) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("❯ ", style = MaterialTheme.typography.labelLarge, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
            Text(
                msg.content,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// ============ 助手消息：全宽流式 + 光标 ============
@Composable
fun AssistantStream(msg: Message, thinkingExpandedDefault: Boolean = false) {
    Column {
        // 思考块（折叠）
        if (!msg.thinking.isNullOrBlank()) {
            ThinkingBlock(msg.thinking!!, thinkingExpandedDefault)
        }
        if (msg.content.isBlank() && msg.toolCalls.isEmpty() && msg.thinking.isNullOrBlank()) {
            // 等待首包：呼吸点
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(4.dp)))
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(4.dp)))
                Text("正在思考…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun ThinkingBlock(text: String, expandedDefault: Boolean) {
    var expanded by remember { mutableStateOf(expandedDefault) }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
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
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============ 工具行（Claude Code 符号语言：⏺ 与 ⎿） ============
@Composable
fun ToolLine(msg: Message) {
    var expanded by remember { mutableStateOf(false) }
    val result = msg.toolResult
    val status = msg.toolStatus
    val hasResult = !result.isNullOrBlank()
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        // ⏺ 主行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Text("⏺", color = when (status) {
                ToolStatus.ERROR -> MaterialTheme.colorScheme.error
                ToolStatus.DENIED -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            })
            Spacer(Modifier.width(8.dp))
            Text(
                msg.toolName ?: "tool",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = Mono,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            when (status) {
                ToolStatus.RUNNING, ToolStatus.PENDING ->
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                ToolStatus.AWAITING_APPROVAL ->
                    Icon(Icons.Filled.HourglassTop, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.tertiary)
                ToolStatus.DONE ->
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                ToolStatus.ERROR ->
                    Icon(Icons.Filled.ErrorOutline, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                ToolStatus.DENIED ->
                    Icon(Icons.Filled.Close, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasResult) {
                Spacer(Modifier.width(6.dp))
                Text(
                    formatSize(result!!.length),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "展开",
                Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // ⎿ 结果行
        if (hasResult && !expanded) {
            Text(
                "⎿ " + result!!.lineSequence().firstOrNull { it.isNotBlank() }?.take(64)?.plus(if ((result.length) > 64) "…" else ""),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = Mono,
                color = if (status == ToolStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        if (expanded) {
            result?.let {
                Text(
                    "⎿ 结果：\n$it",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = Mono,
                    color = if (status == ToolStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp).background(
                        MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp),
                    ).padding(8.dp),
                )
            }
        }
    }
}

private fun formatSize(chars: Int): String = when {
    chars < 1024 -> "${chars}B"
    chars < 1024 * 1024 -> "%.1fKB".format(chars / 1024.0)
    else -> "%.1fMB".format(chars / 1024.0 / 1024.0)
}

// ============ 审批卡片（拒绝/允许一次/总是允许） ============
@Composable
fun ApprovalCard(
    reason: String,
    toolName: String,
    onAllow: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠", color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(6.dp))
                Text("权限确认 · $toolName", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.padding(4.dp))
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.padding(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                TextButton(onClick = onDeny) { Text("拒绝") }
                TextButton(onClick = onAlwaysAllow) { Text("总是允许") }
                FilledIconButton(onClick = onAllow, modifier = Modifier.heightIn(min = 36.dp)) {
                    Icon(Icons.Filled.PlayArrow, "允许一次")
                }
            }
        }
    }
}

// ============ 输入栏（❯ 终端前缀 + IME 适配） ============
@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    running: Boolean,
    modelLabel: String?,
    onPickModel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().imePadding(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("给 DevCodex 下指令…") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Text("❯", fontFamily = Mono, color = MaterialTheme.colorScheme.primary) },
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { if (running) onStop() else onSend() },
                    modifier = Modifier.size(52.dp),
                ) {
                    if (running) Icon(Icons.Filled.Stop, "停止")
                    else Icon(Icons.AutoMirrored.Filled.Send, "发送")
                }
            }
            Text(
                modelLabel ?: "未选择模型，点击设置 →",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = Mono,
                color = if (modelLabel != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 6.dp)
                    .clickable { onPickModel() },
            )
        }
    }
}
