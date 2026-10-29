package ai.devcodex.agent.i18n

import androidx.activity.ComponentActivity

/**
 * 进程内当前宿主 Activity 的强引用。
 *
 * 为什么需要它：
 * MainActivity.attachBaseContext 把 Activity 的 baseContext 换成了本地化 Context，
 * 而 LocalizedContent 又把 LocalContext 换成了 ActivityPreservingContext。
 * activity-compose 的 LocalActivityResultRegistryOwner 默认是「LocalContext 往上找 Activity」，
 * 一旦这条链断了，rememberLauncherForActivityResult 就抛
 * "No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner"。
 *
 * 有了这个引用，界面可以直接把 Activity 自己的 ActivityResultRegistry 提供下去，
 * 完全不经过 Context 链，杜绝该类崩溃。
 */
object HostActivity {
    @Volatile
    var instance: ComponentActivity? = null
        private set

    fun attach(activity: ComponentActivity) {
        instance = activity
    }

    fun detach(activity: ComponentActivity) {
        if (instance === activity) instance = null
    }
}
