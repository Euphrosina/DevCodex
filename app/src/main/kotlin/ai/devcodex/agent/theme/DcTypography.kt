package ai.devcodex.agent.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ai.devcodex.agent.R

/** Google Sans Flex 变量字体（移植自 rikkahub 方案）：weight/grade/ROND/opsz 四轴 */
object FlexFonts {
    val GoogleSansFlex = FontFamily(
        Font(R.font.google_sans_flex, weight = FontWeight.Normal),
        Font(R.font.google_sans_flex, weight = FontWeight.Medium),
        Font(R.font.google_sans_flex, weight = FontWeight.SemiBold),
        Font(R.font.google_sans_flex, weight = FontWeight.Bold),
    )
    val JetBrainsMono = FontFamily(
        Font(R.font.jetbrains_mono, weight = FontWeight.Normal),
        Font(R.font.jetbrains_mono, weight = FontWeight.Medium),
        Font(R.font.jetbrains_mono, weight = FontWeight.SemiBold),
    )
    val System = FontFamily.Default

    val AnthropicSerif by lazy { FontFamily(androidx.compose.ui.text.font.Font(R.font.anthropic_serif)) }
    val OplusSerif by lazy { FontFamily(androidx.compose.ui.text.font.Font(R.font.oplus_serif)) }

    fun byId(id: String): FontFamily = when {
        id.startsWith("custom:") -> ai.devcodex.agent.tools.BuiltinTools.appContext()
            ?.let { CustomFonts.family(it, id.removePrefix("custom:")) } ?: System
        id == "gsf" -> GoogleSansFlex
        id == "mono" -> JetBrainsMono
        id == "serif" -> FontFamily.Serif
        id == "anthropic" -> AnthropicSerif
        id == "oplus" -> OplusSerif
        else -> System
    }
}

private fun flexStyle(size: Int, weight: FontWeight, mono: Boolean = false): TextStyle {
    val family = if (mono) FlexFonts.JetBrainsMono else FlexFonts.GoogleSansFlex
    return TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
    )
}

/** M3 排版：应用字体(appFont) + 对话字体(chatFont) 双轨 */
fun dcTypography(appFont: String = "gsf", chatFont: String = "gsf"): Typography {
    val chatMono = chatFont == "mono"
    val chatFamily = FlexFonts.byId(chatFont)   // 对话字体全量支持（anthropic/oplus/serif/mono/gsf）
    val base = Typography(
        displaySmall = flexStyle(36, FontWeight.Normal),
        headlineLarge = flexStyle(32, FontWeight.Normal),
        headlineMedium = flexStyle(28, FontWeight.Normal),
        headlineSmall = flexStyle(24, FontWeight.Normal),
        titleLarge = flexStyle(22, FontWeight.Normal),
        titleMedium = flexStyle(16, FontWeight.Medium),
        titleSmall = flexStyle(14, FontWeight.Medium),
        bodyLarge = flexStyle(16, FontWeight.Normal, chatMono),
        bodyMedium = flexStyle(14, FontWeight.Normal, chatMono),
        bodySmall = flexStyle(12, FontWeight.Normal, chatMono),
        labelLarge = flexStyle(14, FontWeight.Medium),
        labelMedium = flexStyle(12, FontWeight.Medium),
        labelSmall = flexStyle(11, FontWeight.Medium),
    )
    val appFamily = FlexFonts.byId(appFont)
    // 对话字体（body 三级）应用 chatFont 家族（anthropic/oplus/serif 实时生效）
    val withChat = if (chatFamily == FlexFonts.GoogleSansFlex) base else base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = chatFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = chatFamily),
        bodySmall = base.bodySmall.copy(fontFamily = chatFamily),
    )
    if (appFamily == FlexFonts.GoogleSansFlex) return withChat
    return withChat.copy(
        displaySmall = withChat.displaySmall.copy(fontFamily = appFamily),
        headlineLarge = withChat.headlineLarge.copy(fontFamily = appFamily),
        headlineMedium = withChat.headlineMedium.copy(fontFamily = appFamily),
        headlineSmall = withChat.headlineSmall.copy(fontFamily = appFamily),
        titleLarge = withChat.titleLarge.copy(fontFamily = appFamily),
        titleMedium = withChat.titleMedium.copy(fontFamily = appFamily),
        titleSmall = withChat.titleSmall.copy(fontFamily = appFamily),
        labelLarge = withChat.labelLarge.copy(fontFamily = appFamily),
        labelMedium = withChat.labelMedium.copy(fontFamily = appFamily),
        labelSmall = withChat.labelSmall.copy(fontFamily = appFamily),
    )
}
