package ai.devcodex.agent.i18n

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * 包裹整个界面：按当前生效语言生成本地化 Context，下发 LocalContext / LocalConfiguration / LocalLayoutDirection。
 * 其下所有 stringResource 自动取到目标语言，切换语言时无需重建 Activity。
 */
@Composable
fun LocalizedContent(content: @Composable () -> Unit) {
    val language by AppLocale.effective.collectAsState()
    val base = LocalContext.current
    val localized = remember(language, base) { AppLocale.localized(base, language.toLocale()) }
    val direction = remember(language) {
        if (android.text.TextUtils.getLayoutDirectionFromLocale(language.toLocale()) == android.view.View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }
    // 保留 Activity 引用：对话框、权限请求等仍需通过 Context 链找到 Activity
    val wrapped = remember(localized, base) { ActivityPreservingContext(localized, base) }
    CompositionLocalProvider(
        LocalContext provides wrapped,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides direction,
    ) {
        content()
    }
}

/** 资源取自本地化 Context，Activity 相关查找仍指向原始 Context。 */
private class ActivityPreservingContext(
    private val localized: Context,
    private val original: Context,
) : ContextWrapper(localized) {
    override fun getSystemService(name: String): Any? = original.getSystemService(name)
    override fun getApplicationContext(): Context = original.applicationContext
}
