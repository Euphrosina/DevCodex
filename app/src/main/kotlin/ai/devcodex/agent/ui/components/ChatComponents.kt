package ai.devcodex.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Slider
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
fun UserBubble(msg: Message, nick: String = "我") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),  // rikkahub：尾巴角在右上 topEnd
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                ai.devcodex.agent.m3l.LocalMarkdownRendererTheme provides ai.devcodex.agent.m3l.MarkdownRendererTheme(
                    primary = MaterialTheme.colorScheme.onPrimary,
                    surface = MaterialTheme.colorScheme.primary,
                    outlineVariant = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
                ),
            ) {
                androidx.compose.foundation.layout.Column {
                ai.devcodex.agent.m3l.MixedMarkdownLatex(
                    text = msg.content,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    isStreaming = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
                }
            }
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
                Icon(
                    Icons.Filled.AutoAwesome, null,
                    Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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
                Icon(
                    Icons.Filled.Psychology, null,
                    Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary,
                )
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
    // Operit 式流光边框：运行中渐变描边 + 呼吸
    val running = status == ToolStatus.RUNNING || status == ToolStatus.PENDING
    val tc1 = MaterialTheme.colorScheme.primary
    val tc2 = MaterialTheme.colorScheme.tertiary
    val breath = rememberInfiniteTransition(label = "tool")
    val toff by breath.animateFloat(
        initialValue = 0f, targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "toff",
    )
    val tAlpha by breath.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "talpha",
    )
    val toolBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(tc1, tc2, tc1),
        start = androidx.compose.ui.geometry.Offset(toff - 400f, 0f),
        end = androidx.compose.ui.geometry.Offset(toff, 0f),
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ToolStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                ToolStatus.DENIED, ToolStatus.AWAITING_APPROVAL -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = if (running) androidx.compose.foundation.BorderStroke(1.dp, toolBrush)
                 else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                        CircularProgressIndicator(Modifier.size(13.dp).alpha(tAlpha), strokeWidth = 1.5.dp)
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
    argsJson: String = "",
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, null, Modifier.size(16.dp))
                Text("权限确认 · $toolName", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.padding(3.dp))
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            // P0 审计修复：显示工具实际参数（不再盲批）
            if (argsJson.isNotBlank()) {
                Spacer(Modifier.padding(2.dp))
                var showArgs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        if (showArgs) "▾ 参数" else "▸ 参数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.clickable { showArgs = !showArgs },
                    )
                }
                if (showArgs) {
                    Text(
                        argsJson.take(400),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 6,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().background(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        ).padding(6.dp),
                    )
                }
            }
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
    modelId: String,
    modelLabel: String?,
    curCtx: Int,
    effort: String,
    modelMaxCtx: Int,
    onCtx: (Int) -> Unit,
    onEffort: (String) -> Unit,
    onPickModel: () -> Unit,
    onAttach: () -> Unit = {},
) {
    val effortLevels = listOf("", "low", "medium", "high", "xhigh", "max", "ultra")
    val effortLabels = mapOf("" to "Faster: Off", "low" to "Low", "medium" to "Medium", "high" to "High", "xhigh" to "xHigh", "max" to "Max", "ultra" to "Smarter: Ultra")
    // rikkahub 视觉：顶部大圆角浮层容器
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp, 26.dp, 26.dp, 26.dp),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 8.dp).padding(bottom = 8.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ai.devcodex.agent.ui.components.ModelLogo(modelId.ifBlank { "?" }, 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        modelLabel ?: "＋ 选择模型",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 100.dp),
                    )
                    Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                // 上下文档位胶囊（模型旁）
                Row(
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)).padding(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    listOf(0 to "默认", 262144 to "256k", 1048576 to "1M").forEach { (opt, label) ->
                        val support = opt == 0 || opt <= modelMaxCtx
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (curCtx == opt && support) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(enabled = support) { onCtx(opt) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (curCtx == opt && support) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // ===== 第二行：推理滑块独立一行（MD3 七档，Faster↔Smarter） =====
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                val effortIdx = effortLevels.indexOf(effort).coerceAtLeast(0)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                ) {
                    Text("F", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = effortIdx.toFloat(),
                        onValueChange = { v ->
                            val idx = (v + 0.5f).toInt().coerceIn(0, 6)
                            onEffort(effortLevels[idx])
                        },
                        valueRange = 0f..6f,
                        colors = androidx.compose.material3.SliderColors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledThumbColor = MaterialTheme.colorScheme.primary,
                            disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                            disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledActiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledInactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).height(22.dp).padding(horizontal = 2.dp),
                    )
                    Text("S", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(effortLabels[effort] ?: "Off", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                if (running) {
                    Spacer(Modifier.width(8.dp))
                    Text("● 运行中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
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

