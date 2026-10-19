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
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(1)
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
            } catch (e: Exception) { binding = false }
        }
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

    fun exec(command: String): String = when {
        shizuku() != null -> runCatching { shizuku()!!.exec(command) }.getOrElse { "ERROR(shizuku): ${it.message}" }
        rootAvailable() -> rootExec(command)
        else -> "ERROR: 无 Shizuku/Root 权限，请先在 设置→工具与权限 授权"
    }

    private fun rootExec(command: String): String = try {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.ifBlank { "(exit ${p.exitValue()})" }
    } catch (e: Exception) { "ERROR(root): ${e.message}" }

    fun readFile(path: String): String = when {
        shizuku() != null -> runCatching { shizuku()!!.read(path) }.getOrElse { "ERROR: ${it.message}" }
        else -> runCatching { File(path).readText() }.getOrElse { rootExec("cat \"$path\"") }
    }

    fun writeFile(path: String, data: String): String = when {
        shizuku() != null -> runCatching { shizuku()!!.write(path, data); "OK" }.getOrElse { "ERROR: ${it.message}" }
        else -> {
            val ok = runCatching {
                File(path).parentFile?.mkdirs()
                File(path).writeText(data)
                true
            }.getOrDefault(false)
            if (ok) "OK" else rootWrite(path, data)
        }
    }

    private fun rootWrite(path: String, data: String): String = try {
        val esc = data.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        val p = ProcessBuilder("su", "-c", "mkdir -p \"$(dirname \"$path\")\" && cat > \"$path\"").redirectErrorStream(true).start()
        p.outputStream.write(data.toByteArray())
        p.outputStream.close()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        if (p.exitValue() == 0) "OK" else "ERROR(root write): $out"
    } catch (e: Exception) { "ERROR(root write): ${e.message}" }

    fun listDir(path: String): String = when {
        shizuku() != null -> runCatching { shizuku()!!.list(path) }.getOrElse { "ERROR: ${it.message}" }
        else -> {
            val f = File(path)
            if (f.isDirectory) f.list()?.joinToString("\n") ?: "(empty)"
            else rootExec("ls -la \"$path\"")
        }
    }

    fun delete(path: String): String = when {
        shizuku() != null -> runCatching { if (shizuku()!!.delete(path)) "OK" else "ERROR: 删除失败" }.getOrElse { "ERROR: ${it.message}" }
        else -> if (File(path).deleteRecursively()) "OK" else rootExec("rm -rf \"$path\"").ifBlank { "OK" }
    }
}
