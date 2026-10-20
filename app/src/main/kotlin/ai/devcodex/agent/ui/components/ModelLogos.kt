package ai.devcodex.agent.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.addPathNodes

/** 品牌矢量 Logo（自绘近似形，非官方资产） */
object ModelLogos {

    private fun makeLogo(pathData: String, color: Color): Pair<ImageVector, Color> {
        val builder = androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = "logo", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        )
        builder.addPath(addPathNodes(pathData), fill = androidx.compose.ui.graphics.SolidColor(color))
        return builder.build() to color
    }

    /** OpenAI 六瓣花（近似） */
    private val openai = makeLogo(
        "M12,2 C13.8,2 15.4,3.1 16.2,4.7 L18.5,9.1 C20.5,9.5 22,11.3 22,13.4 C22,15.8 20,17.8 17.6,17.8 C16.9,17.8 16.3,17.6 15.7,17.3 L10.2,20.2 C9.4,21.3 8.1,22 6.7,22 C4.2,22 2.2,20 2.2,17.6 C2.2,15.8 3.3,14.2 4.9,13.4 L7.3,9.1 C7.4,6.8 9.3,5 11.6,5 C11.7,5 11.9,5 12,5 Z M11.6,6.8 C10.4,6.8 9.4,7.8 9.4,9 C9.4,10.2 10.4,11.2 11.6,11.2 C12.8,11.2 13.8,10.2 13.8,9 C13.8,7.8 12.8,6.8 11.6,6.8 Z M6.7,15.9 C5.5,15.9 4.5,16.9 4.5,18.1 C4.5,19.3 5.5,20.3 6.7,20.3 C7.9,20.3 8.9,19.3 8.9,18.1 C8.9,16.9 7.9,15.9 6.7,15.9 Z M17.6,12.1 C16.4,12.1 15.4,13.1 15.4,14.3 C15.4,15.5 16.4,16.5 17.6,16.5 C18.8,16.5 19.8,15.5 19.8,14.3 C19.8,13.1 18.8,12.1 17.6,12.1 Z",
        Color(0xFF10A37F))

    /** Claude 星爆（近似） */
    private val claude = makeLogo(
        "M12,3.4 L13.7,8.2 L18.8,6.9 L15.4,10.9 L19.8,13.7 L14.6,13.9 L15.2,19 L12,15 L8.8,19 L9.4,13.9 L4.2,13.7 L8.6,10.9 L5.2,6.9 L10.3,8.2 Z",
        Color(0xFFD97757))

    /** DeepSeek 鲸尾（近似） */
    private val deepseek = makeLogo(
        "M4,7 C6,5 9,4 12,5 C15,6 17,8 18,11 C19,13 18.5,16 16.5,17.5 C15,18.5 13,19 11,18.5 C12.5,17.5 13.5,16 13.5,14 C13.5,11 11.5,8.5 8.5,8 C7,7.7 5.5,7.2 4,7 Z M8,12 C8.8,12 9.5,12.7 9.5,13.5 C9.5,14.3 8.8,15 8,15 C7.2,15 6.5,14.3 6.5,13.5 C6.5,12.7 7.2,12 8,12 Z",
        Color(0xFF4D6BFE))

    /** Gemini 四芒星（近似） */
    private val gemini = makeLogo(
        "M12,2 C13,6 15.5,8.5 19.5,9.5 C15.5,10.5 13,13 12,17 C11,13 8.5,10.5 4.5,9.5 C8.5,8.5 11,6 12,2 Z M18.5,15 C19,16.8 20.2,18 22,18.5 C20.2,19 19,20.2 18.5,22 C18,20.2 16.8,19 15,18.5 C16.8,18 18,16.8 18.5,15 Z",
        Color(0xFF4285F4))

    /** GLM 螺旋（近似） */
    private val glm = makeLogo(
        "M12,2 C7,2 3,6 3,11 C3,13 3.8,14.8 5,16.2 L5,11 C5,7.7 7.7,5 11,5 C14.3,5 17,7.7 17,11 C17,14.3 14.3,17 11,17 L8.5,17 C9.5,18.3 11,19 12.7,19 L12,22 C17,22 21,18 21,13 C21,8 17,4 12,4 Z",
        Color(0xFF3859FF))

    /** Qwen 波纹（近似） */
    private val qwen = makeLogo(
        "M3,9 C5,7 8,6.5 10,8 C11,8.6 11,10 10,10.7 C8.5,11.8 6,11.5 4.5,13 C3.6,14 3.6,15.5 4.8,16.3 C7,17.8 10.5,17.5 12.5,15.5 C14,14 14.5,11.5 16,10 C17.5,8.6 19.8,8.3 21,9.5 C21.5,10 21.5,11 21,11.5 C19.5,12.8 17.5,12.5 16.2,13.7 C15,15 14.5,17 13,18.3 C10.5,20.3 6.5,20.5 4,18.5 C2.2,17.2 1.8,14.5 3.2,12.7 C4,11.7 5,11 6,10.4 C4.8,10.2 3.7,9.7 3,9 Z",
        Color(0xFF615CED))

    /** Kimi 月牙 */
    private val kimi = makeLogo(
        "M15,3 C10,3 6,7 6,12 C6,17 10,21 15,21 C16.3,21 17.5,20.7 18.6,20.2 C14.9,19 12.2,15.8 12.2,12 C12.2,8.2 14.9,5 18.6,3.8 C17.5,3.3 16.3,3 15,3 Z",
        Color(0xFF1F2937))

    /** Llama 圆环 */
    private val llama = makeLogo(
        "M12,2.5 A9.5,9.5 0 1,0 12,21.5 A9.5,9.5 0 1,0 12,2.5 Z M12,5.5 A6.5,6.5 0 1,1 12,18.5 A6.5,6.5 0 1,1 12,5.5 Z M12,8.5 A3.5,3.5 0 1,0 12,15.5 A3.5,3.5 0 1,0 12,8.5 Z",
        Color(0xFF0668E1))

    /** 通用（其他模型：渐变星形） */
    private val generic = makeLogo(
        "M12,2 L14.2,9.8 L22,12 L14.2,14.2 L12,22 L9.8,14.2 L2,12 L9.8,9.8 Z",
        Color(0xFF8B5CF6))

    fun forModel(model: String): Pair<ImageVector, Color> = when {
        model.contains("gpt", true) || model.contains("o1") || model.contains("o3") || model.contains("o4") || model.contains("openai", true) -> openai
        model.contains("claude", true) || model.contains("anthropic", true) -> claude
        model.contains("deepseek", true) -> deepseek
        model.contains("gemini", true) -> gemini
        model.contains("glm", true) || model.contains("zhipu", true) -> glm
        model.contains("qwen", true) || model.contains("通义", true) -> qwen
        model.contains("kimi", true) || model.contains("moonshot", true) -> kimi
        model.contains("llama", true) -> llama
        else -> generic
    }
}

/** 模型 Logo 组件（替换字母圆标） */
@Composable
fun ModelLogo(model: String, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val (icon, tint) = ModelLogos.forModel(model)
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, model, Modifier.size(size * 0.62f), tint = tint)
    }
}
