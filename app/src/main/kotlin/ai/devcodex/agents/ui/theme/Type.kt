package ai.devcodex.agents.ui.theme

import ai.devcodex.agents.R
import ai.devcodex.agents.data.model.AppFont
import ai.devcodex.agents.data.model.FontWeightSetting
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val DefaultStyles = Typography()

fun fontFamilyFor(font: AppFont): FontFamily = when (font) {
    AppFont.DEFAULT -> FontFamily.Default
    AppFont.SANS -> FontFamily.SansSerif
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONO -> FontFamily.Monospace
    AppFont.CUSTOM -> FontFamily(Font(R.font.lxgw_wenkai_regular))
    AppFont.SMILEY -> FontFamily(Font(R.font.smiley_sans_oblique))
    AppFont.KAITI_GB2312 -> FontFamily(Font(R.font.kaiti_gb2312))
    AppFont.FZ_HEITI_FANTI -> FontFamily(Font(R.font.fangzheng_heiti_fanti))
    AppFont.FZ_HEITI_JIANTI -> FontFamily(Font(R.font.fangzheng_heiti_jianti))
    AppFont.FZ_HEITI_JZF -> FontFamily(Font(R.font.fangzheng_heiti_jzf))
    AppFont.YIQI_XINSONGTI -> FontFamily(Font(R.font.yiqi_xinsongti))
    AppFont.XIAOMI_LANTING -> FontFamily(Font(R.font.xiaomi_lanting_normal))
    AppFont.MSYH -> FontFamily(Font(R.font.msyh))
    AppFont.PINGFANG_ZHONGHEI -> FontFamily(Font(R.font.pingfang_zhonghei_jian))
    AppFont.PINGFANG_ZHUN -> FontFamily(Font(R.font.pingfang_zhun_jian))
    AppFont.MAPLE_MONO -> FontFamily(Font(R.font.maple_mono_medium))
    AppFont.MAPLE_MONO_ITALIC -> FontFamily(Font(R.font.maple_mono_medium_italic))
    AppFont.GOOGLE_SANS_CODE -> FontFamily(Font(R.font.googlesanscode_mono))
}

fun fontWeightFor(setting: FontWeightSetting): FontWeight = when (setting) {
    FontWeightSetting.LIGHT -> FontWeight.Light
    FontWeightSetting.NORMAL -> FontWeight.Normal
    FontWeightSetting.MEDIUM -> FontWeight.Medium
    FontWeightSetting.BOLD -> FontWeight.Bold
}

fun appTypography(
    font: AppFont,
    weightSetting: FontWeightSetting,
    fontSize: TextUnit = 16.sp
): Typography {
    val family = fontFamilyFor(font)
    val weight = fontWeightFor(weightSetting)

    fun apply(style: TextStyle): TextStyle = style.copy(
        fontFamily = family,
        fontSize = fontSize,
        fontWeight = weight
    )

    return Typography(
        displayLarge = apply(DefaultStyles.displayLarge),
        displayMedium = apply(DefaultStyles.displayMedium),
        displaySmall = apply(DefaultStyles.displaySmall),
        headlineLarge = apply(DefaultStyles.headlineLarge),
        headlineMedium = apply(DefaultStyles.headlineMedium),
        headlineSmall = apply(DefaultStyles.headlineSmall),
        titleLarge = apply(DefaultStyles.titleLarge),
        titleMedium = apply(DefaultStyles.titleMedium),
        titleSmall = apply(DefaultStyles.titleSmall),
        bodyLarge = apply(DefaultStyles.bodyLarge),
        bodyMedium = apply(DefaultStyles.bodyMedium),
        bodySmall = apply(DefaultStyles.bodySmall),
        labelLarge = apply(DefaultStyles.labelLarge),
        labelMedium = apply(DefaultStyles.labelMedium),
        labelSmall = apply(DefaultStyles.labelSmall)
    )
}