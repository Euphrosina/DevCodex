package ai.devcodex.agent.i18n

import android.content.Context
import ai.devcodex.agent.R
import ai.devcodex.agent.util.DebugLog

/**
 * 读取随语言切换的内置系统提示词。
 * 资源位于 res/raw/default_system_prompt.txt 与 raw-<lang>/，由资源限定符按当前生效语言解析。
 */
object PromptProvider {

    private const val FALLBACK =
        "You are DevCodex, an AI agent running on an Android phone. Use tools to inspect and modify the device, and answer concisely."

    fun systemPrompt(base: Context): String {
        val ctx = AppLocale.context(base)
        return runCatching {
            ctx.resources.openRawResource(R.raw.default_system_prompt)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .trim()
        }.getOrElse {
            DebugLog.e("Prompt", "读取系统提示词失败: ${it.message}", it)
            FALLBACK
        }.ifBlank { FALLBACK }
    }
}
