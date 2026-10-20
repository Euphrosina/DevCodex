package ai.devcodex.agent.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import ai.devcodex.agent.theme.PresetTheme

/** MChat 主题移植（色值取自 M-Foundry/MChat，遵循其开源许可） */

private fun mchatTheme(
    id: String, name: String,
    lightPrimary: Color, lightOnPrimary: Color,
    darkPrimary: Color, darkOnPrimary: Color,
    sameDayNight: Boolean = false,
): PresetTheme {
    fun darkP() = if (sameDayNight) lightPrimary else darkPrimary
    fun darkOn() = if (sameDayNight) lightOnPrimary else darkOnPrimary
    return PresetTheme(
        id = id, name = name,
        light = lightColorScheme(
            primary = lightPrimary, onPrimary = lightOnPrimary,
            primaryContainer = lightPrimary.copy(alpha = 0.22f),
            onPrimaryContainer = lightPrimary.copy(alpha = 0.85f).compositeOverDark(),
            secondary = lightPrimary.copy(alpha = 0.8f), onSecondary = lightOnPrimary,
            background = Color(0xFFFFFBFF), onBackground = Color(0xFF1C1B1F),
            surface = Color(0xFFFFFBFF), onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
        ),
        dark = darkColorScheme(
            primary = darkP(), onPrimary = darkOn(),
            primaryContainer = darkP().copy(alpha = 0.3f),
            onPrimaryContainer = darkP().copy(alpha = 0.9f).compositeOverDark(),
            secondary = darkP().copy(alpha = 0.8f), onSecondary = darkOn(),
            background = Color(0xFF1C1B1F), onBackground = Color(0xFFE6E1E5),
            surface = Color(0xFF1C1B1F), onSurface = Color(0xFFE6E1E5),
            surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
        ),
    )
}

/** 混到深色底（简易合成，避免低对比） */
private fun Color.compositeOverDark(): Color =
    Color(
        red = red * 0.55f + 0.45f * 0.08f,
        green = green * 0.55f + 0.45f * 0.08f,
        blue = blue * 0.55f + 0.45f * 0.08f,
        alpha = 1f,
    )

val MiyouOrangeTheme = mchatTheme("miyou", "米柚橙", Color(0xFFFF7E00), Color(0xFFFFFFFF), Color(0xFFE86F00), Color(0xFFFFFFFF))
val PureWhiteTheme = mchatTheme("purewhite", "纯白", Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF000000))
val ClassicGreenTheme = mchatTheme("green", "经典绿", Color(0xFF006E2A), Color(0xFFFFFFFF), Color(0xFF5CDB78), Color(0xFF003913))
val ClassicPurpleTheme = mchatTheme("purple", "经典紫", Color(0xFF6650A4), Color(0xFFFFFFFF), Color(0xFFD0BCFF), Color(0xFF381E72))
val MikuGreenTheme = mchatTheme("miku", "初音绿", Color(0xFF39C5BB), Color(0xFF000000), Color(0xFF39C5BB), Color(0xFF000000), sameDayNight = true)
val TetoRedTheme = mchatTheme("teto", "重音红", Color(0xFFD93A49), Color(0xFFFFFFFF), Color(0xFFD93A49), Color(0xFFFFFFFF), sameDayNight = true)
val DeepBlueTheme = mchatTheme("deepblue", "深空蓝", Color(0xFF0B57D0), Color(0xFFFFFFFF), Color(0xFFA8C7FA), Color(0xFF062E6F))
val SilenceGreenTheme = mchatTheme("silence", "静默绿", Color(0xFF5EA0AA), Color(0xFFFFFFFF), Color(0xFF8FCED0), Color(0xFF00363A))
