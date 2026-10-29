package ai.devcodex.agent.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageResolverTest {

    private fun system(vararg tags: String) = tags.map { Locale.forLanguageTag(it) }

    private fun resolve(vararg tags: String) =
        LanguageResolver.resolve(AppLanguage.SYSTEM, system(*tags))

    @Test
    fun explicitChoiceIsReturnedAsIs() {
        for (lang in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) {
            assertEquals(lang, LanguageResolver.resolve(lang, system("fr-FR")))
        }
    }

    @Test
    fun simplifiedChineseMapsToZhCn() {
        assertEquals(AppLanguage.ZH_CN, resolve("zh-CN"))
        assertEquals(AppLanguage.ZH_CN, resolve("zh-Hans-CN"))
        assertEquals(AppLanguage.ZH_CN, resolve("zh"))
        assertEquals(AppLanguage.ZH_CN, resolve("zh-SG"))
    }

    @Test
    fun traditionalTaiwanMapsToZhTw() {
        assertEquals(AppLanguage.ZH_TW, resolve("zh-TW"))
        assertEquals(AppLanguage.ZH_TW, resolve("zh-Hant-TW"))
        assertEquals(AppLanguage.ZH_TW, resolve("zh-Hant"))
    }

    @Test
    fun hongKongAndMacauMapToZhHk() {
        assertEquals(AppLanguage.ZH_HK, resolve("zh-HK"))
        assertEquals(AppLanguage.ZH_HK, resolve("zh-Hant-HK"))
        assertEquals(AppLanguage.ZH_HK, resolve("zh-MO"))
        assertEquals(AppLanguage.ZH_HK, resolve("zh-Hant-MO"))
    }

    @Test
    fun englishJapaneseKoreanMapDirectly() {
        assertEquals(AppLanguage.EN, resolve("en-US"))
        assertEquals(AppLanguage.EN, resolve("en-GB"))
        assertEquals(AppLanguage.JA, resolve("ja-JP"))
        assertEquals(AppLanguage.KO, resolve("ko-KR"))
    }

    @Test
    fun firstSupportedLocaleInListWins() {
        assertEquals(AppLanguage.JA, resolve("fr-FR", "ja-JP", "en-US"))
        assertEquals(AppLanguage.KO, resolve("ko-KR", "ja-JP"))
    }

    @Test
    fun noSupportedLocaleFallsBackToEnglish() {
        assertEquals(AppLanguage.EN, resolve("fr-FR", "de-DE"))
        assertEquals(AppLanguage.EN, LanguageResolver.resolve(AppLanguage.SYSTEM, emptyList()))
    }

    @Test
    fun resolveNeverReturnsSystem() {
        val samples = listOf("zh-CN", "zh-TW", "zh-HK", "en", "ja", "ko", "fr", "ar", "und")
        for (tag in samples) {
            assertEquals(false, resolve(tag) == AppLanguage.SYSTEM)
        }
    }

    @Test
    fun resolveIsDeterministic() {
        val input = system("fr-FR", "zh-Hant-HK")
        assertEquals(
            LanguageResolver.resolve(AppLanguage.SYSTEM, input),
            LanguageResolver.resolve(AppLanguage.SYSTEM, input),
        )
    }

    @Test
    fun fromStoredAcceptsNameTagAndRejectsGarbage() {
        assertEquals(AppLanguage.ZH_TW, AppLanguage.fromStored("ZH_TW"))
        assertEquals(AppLanguage.ZH_TW, AppLanguage.fromStored("zh-TW"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored("xx"))
    }
}
