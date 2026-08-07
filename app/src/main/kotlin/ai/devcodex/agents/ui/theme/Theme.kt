package ai.devcodex.agents.ui.theme

import ai.devcodex.agents.data.model.AccentColor
import ai.devcodex.agents.data.model.AppFont
import ai.devcodex.agents.data.model.FontWeightSetting
import ai.devcodex.agents.data.model.ThemeMode
import ai.devcodex.agents.data.model.ThemePreset
import ai.devcodex.agents.data.model.UiStyle
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MIUI 风格大圆角（HyperOS 视觉语言）
val MiuiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private data class AccentPalette(val lightPrimary: Color, val darkPrimary: Color)

private val AccentPalettes = mapOf(
    AccentColor.PURPLE to AccentPalette(Purple40, Purple80),
    AccentColor.BLUE to AccentPalette(BluePrimary, BluePrimaryDark),
    AccentColor.GREEN to AccentPalette(GreenPrimary, GreenPrimaryDark),
    AccentColor.ORANGE to AccentPalette(OrangePrimary, OrangePrimaryDark),
    AccentColor.PINK to AccentPalette(PinkPrimary, PinkPrimaryDark),
    AccentColor.TEAL to AccentPalette(TealPrimary, TealPrimaryDark)
)

fun accentPrimaryColor(accent: AccentColor, darkTheme: Boolean): Color {
    val palette = AccentPalettes.getValue(accent)
    return if (darkTheme) palette.darkPrimary else palette.lightPrimary
}

// 预设主题完整配色（借鉴知名主题的标准色板）
private data class ThemeColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color
)

private val ThemePresets = mapOf(
    ThemePreset.DARK to ThemeColors(
        primary = Color(0xFFBB86FC), onPrimary = Color(0xFF000000),
        secondary = Color(0xFF03DAC6), tertiary = Color(0xFFCF6679),
        background = Color(0xFF121212), onBackground = Color(0xFFE1E1E1),
        surface = Color(0xFF1E1E1E), onSurface = Color(0xFFE1E1E1)
    ),
    ThemePreset.LIGHT to ThemeColors(
        primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260),
        background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1D1B20)
    ),
    ThemePreset.COFFEE to ThemeColors(
        primary = Color(0xFFC8A27D), onPrimary = Color(0xFF2E2119),
        secondary = Color(0xFFA97C50), tertiary = Color(0xFF8D5B2F),
        background = Color(0xFF2E2119), onBackground = Color(0xFFEFE4D8),
        surface = Color(0xFF3A2A20), onSurface = Color(0xFFEFE4D8)
    ),
    ThemePreset.VSCODE to ThemeColors(
        primary = Color(0xFF007ACC), onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF4FC1FF), tertiary = Color(0xFFC586C0),
        background = Color(0xFF1E1E1E), onBackground = Color(0xFFD4D4D4),
        surface = Color(0xFF252526), onSurface = Color(0xFFD4D4D4)
    ),
    ThemePreset.GITHUB_DARK to ThemeColors(
        primary = Color(0xFF58A6FF), onPrimary = Color(0xFF0D1117),
        secondary = Color(0xFF3FB950), tertiary = Color(0xFFF778BA),
        background = Color(0xFF0D1117), onBackground = Color(0xFFC9D1D9),
        surface = Color(0xFF161B22), onSurface = Color(0xFFC9D1D9)
    ),
    ThemePreset.GRUVBOX to ThemeColors(
        primary = Color(0xFF83A598), onPrimary = Color(0xFF282828),
        secondary = Color(0xFFB8BB26), tertiary = Color(0xFFFB4934),
        background = Color(0xFF282828), onBackground = Color(0xFFEBDBB2),
        surface = Color(0xFF3C3836), onSurface = Color(0xFFEBDBB2)
    ),
    ThemePreset.HIGH_CONTRAST to ThemeColors(
        primary = Color(0xFFFFD400), onPrimary = Color(0xFF000000),
        secondary = Color(0xFF00FF00), tertiary = Color(0xFF00D4FF),
        background = Color(0xFF000000), onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000), onSurface = Color(0xFFFFFFFF)
    )
)

private fun luminance(color: Color): Double {
    val r = color.red.toDouble()
    val g = color.green.toDouble()
    val b = color.blue.toDouble()
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun contrastText(color: Color): Color = if (luminance(color) > 0.55) Color.Black else Color.White

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t
)

private fun buildScheme(c: ThemeColors): ColorScheme {
    val surfaceVariant = blend(c.surface, c.onSurface, 0.08f)
    return darkColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        secondary = c.secondary,
        onSecondary = contrastText(c.secondary),
        tertiary = c.tertiary,
        onTertiary = contrastText(c.tertiary),
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = c.onSurface.copy(alpha = 0.72f)
    )
}

private fun baseColorsFor(themePreset: ThemePreset, accentColor: AccentColor, darkTheme: Boolean): ThemeColors {
    if (themePreset != ThemePreset.NONE) return ThemePresets.getValue(themePreset)
    val palette = AccentPalettes.getValue(accentColor)
    val primary = if (darkTheme) palette.darkPrimary else palette.lightPrimary
    return ThemeColors(
        primary = primary,
        onPrimary = contrastText(primary),
        secondary = if (darkTheme) PurpleGrey80 else PurpleGrey40,
        tertiary = if (darkTheme) Pink80 else Pink40,
        background = if (darkTheme) Color(0xFF121212) else Color(0xFFFDF7FF),
        onBackground = if (darkTheme) Color(0xFFE1E1E1) else Color(0xFF1D1B20),
        surface = if (darkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF),
        onSurface = if (darkTheme) Color(0xFFE1E1E1) else Color(0xFF1D1B20)
    )
}

@Composable
fun DevCodexTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.PURPLE,
    font: AppFont = AppFont.DEFAULT,
    fontWeight: FontWeightSetting = FontWeightSetting.NORMAL,
    fontSize: TextUnit = 16.sp,
    themePreset: ThemePreset = ThemePreset.NONE,
    customPrimary: Long? = null,
    customBackground: Long? = null,
    uiStyle: UiStyle = UiStyle.MATERIAL,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        themePreset == ThemePreset.NONE && dynamicColor && accentColor == AccentColor.PURPLE &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val base = baseColorsFor(themePreset, accentColor, darkTheme)
            val primary = customPrimary?.let { Color(it) } ?: base.primary
            val background = customBackground?.let { Color(it) } ?: base.background
            val surface = if (customBackground != null) background else base.surface
            buildScheme(
                base.copy(
                    primary = primary,
                    onPrimary = contrastText(primary),
                    background = background,
                    onBackground = contrastText(background),
                    surface = surface,
                    onSurface = contrastText(surface)
                )
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(font, fontWeight, fontSize),
        shapes = if (uiStyle == UiStyle.MIUI) MiuiShapes else Shapes(),
        content = content
    )
}