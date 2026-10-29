package ai.devcodex.agent

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.devcodex.agent.theme.ColorMode
import ai.devcodex.agent.theme.DcTheme
import ai.devcodex.agent.theme.ThemePrefs
import ai.devcodex.agent.ui.AppRoot

class MainActivity : ComponentActivity() {
    // 在 Activity 创建前同步读取语言镜像，保证冷启动首帧即为目标语言
    override fun attachBaseContext(newBase: android.content.Context) {
        val localized = runCatching {
            ai.devcodex.agent.i18n.AppLocale.init(newBase)
            ai.devcodex.agent.i18n.AppLocale.context(newBase)
        }.getOrNull() ?: newBase
        super.attachBaseContext(localized)
    }

    /** 不依赖 Compose 的崩溃报告页：Compose 崩掉时也能把堆栈给用户复制出来。 */
    private fun showCrashReport(report: String) {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val tv = android.widget.TextView(this).apply {
            this.text = report
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val copy = android.widget.Button(this).apply {
            this.text = "复制崩溃报告"
            setOnClickListener {
                runCatching {
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", report))
                    android.widget.Toast.makeText(this@MainActivity, "已复制，请发给开发者", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        val skip = android.widget.Button(this).apply {
            this.text = "清除并继续启动"
            setOnClickListener {
                runCatching { ai.devcodex.agent.util.CrashHandler.clear(this@MainActivity) }
                runCatching { java.io.File(filesDir, "last_start_error.txt").delete() }
                recreate()
            }
        }
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            addView(copy, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            addView(skip, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(tv, android.widget.LinearLayout.LayoutParams(-1, -2))
            addView(row, android.widget.LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(android.widget.ScrollView(this).apply { addView(container) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // SDK 29+ 关闭导航栏对比度强制（edge-to-edge skill 规范）
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        val prevCrash = runCatching { ai.devcodex.agent.util.CrashHandler.latestReport(this) }.getOrNull()
            ?: runCatching {
                java.io.File(filesDir, "last_start_error.txt").takeIf { it.exists() }?.readText()
            }.getOrNull()
        if (!prevCrash.isNullOrBlank()) {
            showCrashReport(prevCrash)
            return
        }
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.FACTORY)
            val settings by vm.settings.collectAsState(initial = ai.devcodex.agent.data.AppSettings())
            // DataStore 中的语言与镜像不一致时（镜像缺失或被清除）以 DataStore 为准
            androidx.compose.runtime.LaunchedEffect(settings.language) {
                val stored = ai.devcodex.agent.i18n.AppLanguage.fromStored(settings.language)
                if (ai.devcodex.agent.i18n.AppLocale.setting.value != stored && settings.language != "SYSTEM") {
                    ai.devcodex.agent.i18n.AppLocale.set(applicationContext, stored)
                }
            }
            ai.devcodex.agent.i18n.LocalizedContent {
                DcTheme(prefs = ThemePrefs(
                    presetId = settings.theme.presetId,
                    colorMode = runCatching { ColorMode.valueOf(settings.theme.colorMode) }.getOrDefault(ColorMode.SYSTEM),
                    dynamicColor = settings.theme.dynamicColor,
                    amoled = settings.theme.amoled,
                    appFont = settings.theme.appFont,
                    chatFont = settings.theme.chatFont,
                )) {
                    AppRoot(vm)
                }
            }
        }
    }
}
