package ai.devcodex.agent.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import ai.devcodex.agent.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 应用语言状态中心。
 * 语言选项同步镜像到 SharedPreferences（DataStore 是异步读取，冷启动首帧需要同步拿到语言）。
 */
object AppLocale {
    private const val PREFS = "locale_prefs"
    private const val KEY = "language"

    private val settingFlow = MutableStateFlow(AppLanguage.SYSTEM)
    private val effectiveFlow = MutableStateFlow(AppLanguage.EN)

    /** 用户选项（可能为 SYSTEM）。 */
    val setting: StateFlow<AppLanguage> get() = settingFlow

    /** 当前生效语言（永不为 SYSTEM）。 */
    val effective: StateFlow<AppLanguage> get() = effectiveFlow

    @Volatile
    private var snapshot: Locale = Locale.ENGLISH

    /** 供后台线程读取的当前 Locale 快照。 */
    val current: Locale get() = snapshot

    @Volatile
    private var appCtx: Context? = null

    /** 在 Application.onCreate 最早期调用：供对象初始化阶段（早于 init）取串。 */
    fun attach(ctx: Context) {
        if (appCtx == null) appCtx = ctx.applicationContext
    }

    /** 在 attachBaseContext 与 Application.onCreate 中调用：同步读取镜像并解析。 */
    fun init(ctx: Context) {
        val stored = runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.onFailure { DebugLog.e("Locale", "读取语言镜像失败: ${it.message}", it) }.getOrNull()
        apply(ctx, AppLanguage.fromStored(stored))
    }

    /** 用户在设置页选择语言，或 DataStore 中的值与镜像不一致时调用。 */
    fun set(ctx: Context, language: AppLanguage) {
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
        }.onFailure { DebugLog.e("Locale", "写入语言镜像失败: ${it.message}", it) }
        apply(ctx, language)
    }

    /** 系统语言变化后（SYSTEM 模式）重新解析。 */
    fun refresh(ctx: Context) = apply(ctx, settingFlow.value)

    private fun apply(ctx: Context, language: AppLanguage) {
        val resolved = LanguageResolver.resolve(language, systemLocales(ctx))
        snapshot = resolved.toLocale()
        settingFlow.value = language
        effectiveFlow.value = resolved
    }

    private fun systemLocales(ctx: Context): List<Locale> {
        // 取系统级 configuration（不受本应用 localeConfig 影响），用于「跟随系统」解析
        @Suppress("UNUSED_PARAMETER")
        val unused = ctx
        val cfg = android.content.res.Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= 24) {
            val list = cfg.locales
            List(list.size()) { list.get(it) }
        } else {
            @Suppress("DEPRECATION")
            listOf(cfg.locale)
        }
    }

    /** 返回带当前生效语言的 Context，供通知、Toast、工具返回等非 Compose 场景取串。 */
    fun context(base: Context): Context = localized(base, snapshot)

    fun localized(base: Context, locale: Locale): Context {
        val cfg = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= 24) {
            cfg.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            cfg.locale = locale
        }
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /** 无 Context 版本：对象初始化阶段 appContext 尚未注入时返回空串，不崩溃。 */
    fun string(resId: Int, vararg args: Any): String {
        val ctx = appCtx ?: return ""
        return string(ctx, resId, *args)
    }

    /** 非 Compose 取串；格式化失败时返回未格式化的原文，避免崩溃。 */
    fun string(base: Context, resId: Int, vararg args: Any): String = runCatching {
        val c = context(base)
        if (args.isEmpty()) c.getString(resId) else c.getString(resId, *args)
    }.getOrElse {
        DebugLog.e("Locale", "取串失败 resId=$resId: ${it.message}", it)
        runCatching { base.getString(resId) }.getOrDefault("")
    }
}
