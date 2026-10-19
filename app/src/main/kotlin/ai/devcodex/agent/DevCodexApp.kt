package ai.devcodex.agent

import android.app.Application
import ai.devcodex.agent.data.PluginRepo
import ai.devcodex.agent.data.SessionRepo
import ai.devcodex.agent.data.SettingsRepo
import ai.devcodex.agent.data.WorkspaceRepo
import ai.devcodex.agent.shell.ShellAccess
import ai.devcodex.agent.tools.BuiltinTools
import ai.devcodex.agent.util.CrashHandler
import rikka.shizuku.Shizuku

class DevCodexApp : Application() {

    companion object { lateinit var instance: DevCodexApp }

    lateinit var settingsRepo: SettingsRepo
    lateinit var sessionRepo: SessionRepo
    lateinit var pluginRepo: PluginRepo
    lateinit var workspaceRepo: WorkspaceRepo

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        instance = this
        settingsRepo = SettingsRepo(this)
        sessionRepo = SessionRepo(this)
        pluginRepo = PluginRepo(this)
        workspaceRepo = WorkspaceRepo(this)
        BuiltinTools.init(this)
        seedPlugins()

        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ShellAccess.bindShizukuService()
            }
        }
        ShellAccess.bindShizukuService()
    }

    // 首启导入内置插件（assets/plugins/*.json，已存在则跳过）
    private fun seedPlugins() {
        val marker = java.io.File(filesDir, ".builtin_plugins_v1")
        if (marker.exists()) return
        runCatching {
            assets.list("plugins")?.forEach { name ->
                runCatching {
                    val text = assets.open("plugins/" + name).bufferedReader().readText()
                    pluginRepo.installFromText(text)
                }
            }
            marker.writeText("ok")
        }
    }
}
