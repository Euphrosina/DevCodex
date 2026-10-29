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
        ai.devcodex.agent.i18n.AppLocale.init(newBase)
        super.attachBaseContext(ai.devcodex.agent.i18n.AppLocale.context(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // SDK 29+ 关闭导航栏对比度强制（edge-to-edge skill 规范）
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
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
