package ai.devcodex.agent.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 动态圆角气泡（行数感知）：
 * - 单/双行：topStart=22 / topEnd=22 / bottomEnd=8(固定) / bottomStart=22
 * - 超过 2 行后每多一行，除 bottomEnd 外三个角 -2dp，最低收缩到 12dp
 * - 文字 16sp / 行高 24sp / padding(16,10)，单行总高 44dp
 */
@Composable
fun DynamicBubble(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF2C2C2E),
    textColor: Color = Color.White,
    maxLines: Int = Int.MAX_VALUE,
    onLongClick: () -> Unit = {},
) {
    var lineCount by remember { mutableIntStateOf(1) }

    Surface(
        color = backgroundColor,
        contentColor = textColor,
        shape = dynamicBubbleShape(lineCount),
        tonalElevation = 0.dp,
        modifier = modifier
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = textColor,
            textAlign = TextAlign.Start,
            maxLines = maxLines,
            onTextLayout = { result -> lineCount = result.lineCount.coerceAtLeast(1) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 根据行数计算圆角：≤2 行基础值；>2 行每行 -2dp，下限 12dp；bottomEnd 恒 8dp */
fun dynamicBubbleShape(lineCount: Int): RoundedCornerShape {
    val shrink = ((lineCount - 2).coerceAtLeast(0) * 2).dp
    val big = (22.dp - shrink).coerceAtLeast(12.dp)
    return RoundedCornerShape(
        topStart = big,
        topEnd = big,
        bottomEnd = 8.dp,
        bottomStart = big,
    )
}
