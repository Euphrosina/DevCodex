package ai.devcodex.agent.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ai.devcodex.agent.R
import ai.devcodex.agent.util.DebugLog

/**
 * 插件开发与使用文档（Markdown）。内容依据 PluginRepo、PluginExecutor 的实际行为编写。
 * 文案按当前生效语言从 res/raw 读取，缺失时回退到英文基线。
 */
private fun loadPluginDoc(context: Context): String = runCatching {
    context.resources.openRawResource(R.raw.plugin_guide).bufferedReader().use { it.readText() }
}.getOrElse {
    DebugLog.e("PluginDocs", "读取插件文档失败: ${it.message}", it)
    ""
}

/** 在 Composable 中取当前语言的插件文档。 */
@Composable
fun pluginDocMarkdown(): String {
    val context = LocalContext.current
    return remember(context.resources.configuration.locales[0]) { loadPluginDoc(context) }
}

/** 供非 Composable 场景取当前语言的插件文档。 */
fun pluginDocMarkdown(context: Context): String = loadPluginDoc(context)
