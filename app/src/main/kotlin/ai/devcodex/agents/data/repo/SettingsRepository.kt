package ai.devcodex.agents.data.repo

import ai.devcodex.agents.data.model.AccentColor
import ai.devcodex.agents.data.model.AppFont
import ai.devcodex.agents.data.model.FontWeightSetting
import ai.devcodex.agents.data.model.ProviderType
import ai.devcodex.agents.data.model.SettingsData
import ai.devcodex.agents.data.model.ThemeMode
import ai.devcodex.agents.data.model.ThemePreset
import ai.devcodex.agents.data.model.UiStyle
import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("codex_settings", Context.MODE_PRIVATE)

    fun load(): SettingsData = SettingsData(
        provider = ProviderType.byName(prefs.getString(KEY_PROVIDER, null) ?: ""),
        baseUrl = prefs.getString(KEY_BASE_URL, null) ?: defaultBaseUrl(),
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        model = prefs.getString(KEY_MODEL, null) ?: defaultModel(),
        temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
        themeMode = ThemeMode.byName(prefs.getString(KEY_THEME_MODE, null) ?: ""),
        accentColor = AccentColor.byName(prefs.getString(KEY_ACCENT_COLOR, null) ?: ""),
        font = AppFont.byName(prefs.getString(KEY_FONT, null) ?: ""),
        fontWeight = FontWeightSetting.byName(prefs.getString(KEY_FONT_WEIGHT, null) ?: ""),
        fontSize = prefs.getFloat(KEY_FONT_SIZE, 16f),
        themePreset = ThemePreset.byName(prefs.getString(KEY_THEME_PRESET, null) ?: ""),
        customPrimary = if (prefs.contains(KEY_CUSTOM_PRIMARY)) prefs.getLong(KEY_CUSTOM_PRIMARY, 0L) else null,
        customBackground = if (prefs.contains(KEY_CUSTOM_BACKGROUND)) prefs.getLong(KEY_CUSTOM_BACKGROUND, 0L) else null,
        uiStyle = UiStyle.byName(prefs.getString(KEY_UI_STYLE, null) ?: "")
    )

    fun save(settings: SettingsData) {
        prefs.edit()
            .putString(KEY_PROVIDER, settings.provider.name)
            .putString(KEY_BASE_URL, settings.baseUrl)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_MODEL, settings.model)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_ACCENT_COLOR, settings.accentColor.name)
            .putString(KEY_FONT, settings.font.name)
            .putString(KEY_FONT_WEIGHT, settings.fontWeight.name)
            .putFloat(KEY_FONT_SIZE, settings.fontSize)
            .putString(KEY_THEME_PRESET, settings.themePreset.name)
            .putString(KEY_UI_STYLE, settings.uiStyle.name)
            .apply()
        val editor = prefs.edit()
        if (settings.customPrimary == null) editor.remove(KEY_CUSTOM_PRIMARY) else editor.putLong(KEY_CUSTOM_PRIMARY, settings.customPrimary)
        if (settings.customBackground == null) editor.remove(KEY_CUSTOM_BACKGROUND) else editor.putLong(KEY_CUSTOM_BACKGROUND, settings.customBackground)
        editor.apply()
    }

    fun saveTreeUri(uri: String) {
        prefs.edit().putString(KEY_TREE_URI, uri).apply()
    }

    fun loadTreeUri(): String? = prefs.getString(KEY_TREE_URI, null)

    private fun defaultBaseUrl() = ProviderType.OPENAI_COMPATIBLE.defaultBaseUrl()
    private fun defaultModel() = ProviderType.OPENAI_COMPATIBLE.defaultModel()

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TREE_URI = "project_tree_uri"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_FONT = "font"
        const val KEY_FONT_WEIGHT = "font_weight"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_CUSTOM_PRIMARY = "custom_primary"
        const val KEY_CUSTOM_BACKGROUND = "custom_background"
        const val KEY_UI_STYLE = "ui_style"
    }
}