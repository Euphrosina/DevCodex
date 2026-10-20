package ai.devcodex.agent.shell

import ai.devcodex.agent.MainActivity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.io.File
import java.util.concurrent.Executors

/** Shell 访问层：Shizuku(ADB 权限) 与 Root(su) 双通道 */
object ShellAccess {

    private const val SHIZUKU_PERM = 0xA001

    private val userServiceArgs by lazy {
        UserServiceArgs(ComponentName("ai.devcodex.agent", "ai.devcodex.agent.shell.ShellService"))
            .daemon(true)   // 常驻：避免每次 unbind 后 Shizuku 端销毁导致的重绑失败
            .processNameSuffix("shell")
            .debuggable(false)
            .version(3)     // 版本升级触发 Shizuku 端刷新旧绑定
    }

    @Volatile private var shizukuService: IShellService? = null
    @Volatile private var binding = false
    private val executor = Executors.newSingleThreadExecutor()

    var onPermissionResult: (() -> Unit)? = null

    fun shizukuAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun shizukuPermissionGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestShizukuPermission() {
        runCatching { Shizuku.requestPermission(SHIZUKU_PERM) }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shizukuService = IShellService.Stub.asInterface(binder)
            binding = false
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            shizukuService = null
        }
    }

    fun bindShizukuService() {
        if (shizukuAvailable() && shizukuPermissionGranted() && shizukuService == null && !binding) {
            try {
                binding = true
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (e: Exception) {
                binding = false
                // 立即二次尝试（Shizuku 端旧绑定清理竞态）
                try { Shizuku.bindUserService(userServiceArgs, serviceConnection) } catch (_: Exception) {}
            }
        }
    }

    /** Shizuku 进程死亡：清引用（监听器回调） */
    fun onShizukuDead() {
        shizukuService = null
        binding = false
    }

    fun unbindShizukuService() {
        runCatching { Shizuku.unbindUserService(userServiceArgs, serviceConnection, true) }
        shizukuService = null
    }

    /** root 可用性检测 */
    fun rootAvailable(): Boolean = try {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("uid=0")
    } catch (e: Exception) { false }

    // ============ 统一操作接口（优先 Shizuku，其次 Root） ============
    enum class Mode { SHIZUKU, ROOT, NONE }

    fun availableMode(): Mode = when {
        shizukuService != null || (shizukuAvailable() && shizukuPermissionGranted()) -> Mode.SHIZUKU
        rootAvailable() -> Mode.ROOT
        else -> Mode.NONE
    }

    private fun shizuku(): IShellService? {
        bindShizukuService()
        return shizukuService
    }

    /** 挂起等待 Shizuku 服务绑定（修复授权后首次调用竞态） */
    private suspend fun shizukuAwait(timeoutMs: Long = 4000): IShellService? {
        bindShizukuService()
        val t0 = System.currentTimeMillis()
        while (shizukuService == null && System.currentTimeMillis() - t0 < timeoutMs) {
            if (!shizukuAvailable() || !shizukuPermissionGranted()) return null
            kotlinx.coroutines.delay(120)
        }
        return shizukuService
    }

    private fun rootExec(command: String): String = try {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.ifBlank { "(exit ${p.exitValue()})" }
    } catch (e: Exception) { "ERROR(root): ${e.message}" }

    private fun rootWrite(path: String, data: String): String = try {
        val p = ProcessBuilder("su", "-c", "mkdir -p \"$(dirname \"$path\")\" && cat > \"$path\"").redirectErrorStream(true).start()
        p.outputStream.write(data.toByteArray())
        p.outputStream.close()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        if (p.exitValue() == 0) "OK" else "ERROR(root write): $out"
    } catch (e: Exception) { "ERROR(root write): ${e.message}" }

    suspend fun exec(command: String): String {
        shizukuAwait()?.let { svc -> return runCatching { svc.exec(command) }.getOrElse { "ERROR(shizuku): ${it.message}" } }
        if (rootAvailable()) return rootExec(command)
        // 细化诊断：告诉用户到底卡在哪一步
        val diag = when {
            !shizukuAvailable() -> "Shizuku 服务未运行（请打开 Shizuku App 启动服务，ADB/无线调试方式）"
            !shizukuPermissionGranted() -> "Shizuku 已运行但未授权本应用（Shizuku App 内点授权，或本应用设置页点「授权」）"
            else -> "Shizuku 已授权但服务绑定失败（试试在设置页点「重新绑定服务」）"
        }
        return "ERROR: Shell 通道不可用。$diag"
    }

    suspend fun readFile(path: String): String {
        shizukuAwait()?.let { svc -> return runCatching { svc.read(path) }.getOrElse { "ERROR: ${it.message}" } }
        return runCatching { File(path).readText() }.getOrElse { rootExec("cat \"$path\"") }
    }

    suspend fun writeFile(path: String, data: String): String {
        shizukuAwait()?.let { svc -> return runCatching { svc.write(path, data); "OK" }.getOrElse { "ERROR: ${it.message}" } }
        val ok = runCatching {
            File(path).parentFile?.mkdirs()
            File(path).writeText(data)
            true
        }.getOrDefault(false)
        return if (ok) "OK" else rootWrite(path, data)
    }

    suspend fun listDir(path: String): String {
        shizukuAwait()?.let { svc -> return runCatching { svc.list(path) }.getOrElse { "ERROR: ${it.message}" } }
        val f = File(path)
        return if (f.isDirectory) (f.list()?.joinToString("\n") ?: "(empty)")
        else rootExec("ls -la \"$path\"")
    }

    suspend fun delete(path: String): String {
        shizukuAwait()?.let { svc -> return runCatching { if (svc.delete(path)) "OK" else "ERROR: 删除失败" }.getOrElse { "ERROR: ${it.message}" } }
        return if (File(path).deleteRecursively()) "OK" else rootExec("rm -rf \"$path\"").ifBlank { "OK" }
    }
}
