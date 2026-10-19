package ai.devcodex.agent

import android.app.Application
import ai.devcodex.agent.data.PluginRepo
import ai.devcodex.agent.data.SessionRepo
import ai.devcodex.agent.data.SettingsRepo
import ai.devcodex.agent.shell.ShellAccess
import ai.devcodex.agent.tools.BuiltinTools
import ai.devcodex.agent.util.CrashHandler
import rikka.shizuku.Shizuku

class DevCodexApp : Application() {

    companion object { lateinit var instance: DevCodexApp }

    lateinit var settingsRepo: SettingsRepo
    lateinit var sessionRepo: SessionRepo
    lateinit var pluginRepo: PluginRepo
    lateinit var workspaceRepo: ai.devcodex.agent.data.WorkspaceRepo

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepo = SettingsRepo(this)
        sessionRepo = SessionRepo(this)
        pluginRepo = PluginRepo(this)
        workspaceRepo = ai.devcodex.agent.data.WorkspaceRepo(this)
        BuiltinTools.init(this)

        // Shizuku 权限回调 → 尝试绑定服务
        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ShellAccess.bindShizukuService()
            }
        }
        ShellAccess.bindShizukuService()
    }
}
