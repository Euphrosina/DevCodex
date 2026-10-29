package ai.devcodex.agent.i18n

import java.util.Locale

/** 应用支持的语言选项。SYSTEM 表示跟随系统。 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("system", ""),
    ZH_CN("zh-CN", "简体中文"),
    ZH_TW("zh-TW", "繁體中文（台灣）"),
    ZH_HK("zh-HK", "繁體中文（香港）"),
    EN("en", "English"),
    JA("ja", "日本語"),
    KO("ko", "한국어");

    /** 对应的 java.util.Locale；SYSTEM 无固定 Locale。 */
    fun toLocale(): Locale = when (this) {
        SYSTEM -> Locale.getDefault()
        ZH_CN -> Locale.SIMPLIFIED_CHINESE
        ZH_TW -> Locale("zh", "TW")
        ZH_HK -> Locale("zh", "HK")
        EN -> Locale.ENGLISH
        JA -> Locale.JAPANESE
        KO -> Locale.KOREAN
    }

    companion object {
        /** 按存储值解析；值缺失或非法时返回 SYSTEM。 */
        fun fromStored(value: String?): AppLanguage =
            entries.firstOrNull { it.name == value || it.tag == value } ?: SYSTEM
    }
}

/** 语言解析：把用户选项与系统语言列表解析为唯一的生效语言（不会返回 SYSTEM）。 */
object LanguageResolver {
    fun resolve(setting: AppLanguage, systemLocales: List<Locale>): AppLanguage {
        if (setting != AppLanguage.SYSTEM) return setting
        for (l in systemLocales) {
            map(l)?.let { return it }
        }
        return AppLanguage.EN
    }

    private fun map(l: Locale): AppLanguage? = when (l.language.lowercase(Locale.ROOT)) {
        "zh" -> {
            val script = l.script
            val country = l.country.uppercase(Locale.ROOT)
            when {
                country == "HK" || country == "MO" -> AppLanguage.ZH_HK
                country == "TW" || script.equals("Hant", ignoreCase = true) -> AppLanguage.ZH_TW
                else -> AppLanguage.ZH_CN
            }
        }
        "en" -> AppLanguage.EN
        "ja" -> AppLanguage.JA
        "ko" -> AppLanguage.KO
        else -> null
    }
}
