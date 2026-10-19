package ai.devcodex.agent.theme

import androidx.compose.material3.ColorScheme
import ai.devcodex.agent.theme.presets.AutumnThemePreset
import ai.devcodex.agent.theme.presets.BlackThemePreset
import ai.devcodex.agent.theme.presets.ClaudeThemePreset
import ai.devcodex.agent.theme.presets.MinimalThemePreset
import ai.devcodex.agent.theme.presets.OceanThemePreset
import ai.devcodex.agent.theme.presets.SakuraThemePreset
import ai.devcodex.agent.theme.presets.SpringThemePreset

/** 主题预设（色值移植自 rikkahub，遵循其 Apache-2.0 许可） */
data class PresetTheme(
    val id: String,
    val name: String,
    val light: ColorScheme,
    val dark: ColorScheme,
) {
    fun scheme(useDark: Boolean): ColorScheme = if (useDark) dark else light
}

val PresetThemes = listOf(
    SakuraThemePreset,
    OceanThemePreset,
    SpringThemePreset,
    AutumnThemePreset,
    BlackThemePreset,
    MinimalThemePreset,
    ClaudeThemePreset,
)

fun findPresetTheme(id: String): PresetTheme =
    PresetThemes.find { it.id == id } ?: ClaudeThemePreset
