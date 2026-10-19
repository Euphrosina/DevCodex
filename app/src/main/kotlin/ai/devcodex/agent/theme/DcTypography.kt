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

    fun byId(id: String): FontFamily = when (id) {
        "gsf" -> GoogleSansFlex
        "mono" -> JetBrainsMono
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

/** M3 排版（Google Sans Flex 版） */
fun dcTypography(monoChat: Boolean = false): Typography = Typography(
    displaySmall = flexStyle(36, FontWeight.Normal),
    headlineLarge = flexStyle(32, FontWeight.Normal),
    headlineMedium = flexStyle(28, FontWeight.Normal),
    headlineSmall = flexStyle(24, FontWeight.Normal),
    titleLarge = flexStyle(22, FontWeight.Normal),
    titleMedium = flexStyle(16, FontWeight.Medium),
    titleSmall = flexStyle(14, FontWeight.Medium),
    bodyLarge = flexStyle(16, FontWeight.Normal, monoChat),
    bodyMedium = flexStyle(14, FontWeight.Normal, monoChat),
    bodySmall = flexStyle(12, FontWeight.Normal, monoChat),
    labelLarge = flexStyle(14, FontWeight.Medium),
    labelMedium = flexStyle(12, FontWeight.Medium),
    labelSmall = flexStyle(11, FontWeight.Medium),
)
