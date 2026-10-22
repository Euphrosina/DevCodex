package ai.devcodex.agent.shell

import android.content.Context
import java.io.File

/**
 * Termux 原生沙盒（双保险架构）：
 * - 主 shell：静态 busybox（musl 三件套：busybox+ld-musl+libc.musl，零外部依赖）
 *   → 任何设备都能跑（不依赖 bootstrap 的动态库完整性）
 * - 完整环境：Termux 官方 bootstrap（bash/apt 工具链）
 *   bash 若缺库（如 readline），提示用户联网 apt 补包，不影响 busybox sh
 * - exec 走 ld-musl 显式加载 busybox（设备无 /lib/ld-musl，必须 loader 起步）
 */
object TermuxRuntime {

    @Volatile
    private var ready = false

    @Volatile
    var lastError: String = ""

    fun prefix(context: Context): File = File(context.filesDir, "termux/usr")

    fun busybox(context: Context): File = File(prefix(context), "bin/busybox_static")

    fun muslDir(context: Context): File = File(prefix(context), "musl-lib")

    fun bash(context: Context): File = File(prefix(context), "bin/bash")

    fun isReady(context: Context): Boolean =
        busybox(context).isFile && busybox(context).canExecute()

    @Synchronized
    fun ensure(context: Context): Boolean {
        if (ready) return true
        val usr = prefix(context)
        val marker = File(usr.parentFile, ".installed")
        try {
            // ---- 1. busybox 三件套（零依赖主 shell） ----
            val bb = busybox(context)
            val mDir = muslDir(context)
            mDir.mkdirs()
            File(usr, "bin").mkdirs()
            if (!bb.isFile) {
                context.assets.open("busybox").use { i -> bb.outputStream().use { o -> i.copyTo(o) } }
            }
            bb.setExecutable(true, false)
            for (n in listOf("ld-musl-aarch64.so.1", "libc.musl-aarch64.so.1")) {
                val f = File(mDir, n)
                if (!f.isFile) {
                    context.assets.open("busybox_lib/$n").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                }
                f.setExecutable(true, false)
            }
            if (!isReady(context)) {
                lastError = "busybox 解压失败"
                return false
            }
            // ---- 2. bootstrap 完整环境（可失败，不阻塞 busybox） ----
            if (!marker.exists() && !bash(context).isFile) {
                try {
                    val tmp = File(context.cacheDir, "bootstrap.zip")
                    context.assets.open("termux-bootstrap.zip").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    java.util.zip.ZipFile(tmp).use { zf ->
                        val entries = zf.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            val out = File(usr, e.name)
                            if (e.isDirectory) out.mkdirs()
                            else {
                                out.parentFile?.mkdirs()
                                if (!out.exists()) {
                                    zf.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                                }
                            }
                        }
                    }
                    // bin 全可执行 + bin/sh 指向 busybox（稳妥 shell）
                    File(usr, "bin").listFiles()?.forEach { if (it.isFile) it.setExecutable(true, false) }
                    tmp.delete()
                    marker.writeText("bootstrap-1")
                } catch (e: Exception) {
                    // bash 工具链失败不致命（busybox 已可用）
                    if (bash(context).isFile) bash(context).setExecutable(true, false)
                }
            }
            ready = true
            lastError = ""
            return true
        } catch (e: Exception) {
            lastError = "初始化异常: ${e.message}"
            return false
        }
    }

    /** bash 可跑则提示（可选） */
    fun bashReady(context: Context): Boolean = runCatching {
        val p = exec(context, "command -v bash >/dev/null && bash -c 'echo ok'", 8)
        p.contains("ok")
    }.getOrDefault(false)

    /** 执行（busybox ash 为主 shell） */
    fun exec(context: Context, command: String, timeoutSec: Long = 30, cwd: String? = null): String {
        if (!ensure(context)) return "ERROR(termux): 引擎未就绪（$lastError）"
        val usr = prefix(context)
        val loader = File(muslDir(context), "ld-musl-aarch64.so.1").absolutePath
        val bb = busybox(context).absolutePath
        val ws = ai.devcodex.agent.tools.BuiltinTools.workspaceRoot().absolutePath
        val workDir = if (cwd != null && File(cwd).isDirectory()) cwd else ws
        val wrapped = "cd '$workDir' 2>/dev/null; $command"
        return try {
            val p = ProcessBuilder(loader, bb, "sh", "-c", wrapped)
                .redirectErrorStream(true)
                .apply {
                    val env = environment()
                    env["PATH"] = File(usr, "bin").absolutePath + ":/system/bin:/system/xbin"
                    env["LD_LIBRARY_PATH"] = File(usr, "lib").absolutePath
                    env["HOME"] = File(usr, "home").absolutePath
                    File(usr, "home").mkdirs()
                    env["TMPDIR"] = File(usr, "tmp").absolutePath
                    env["LANG"] = "en_US.UTF-8"
                    env["TERM"] = "xterm-256color"
                }
                .start()
            val out = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(timeoutSec * 1000) {
                    val sb = StringBuilder()
                    p.inputStream.bufferedReader().forEachLine { line ->
                        sb.appendLine(line)
                        if (sb.length > 100_000) return@forEachLine
                    }
                    sb.toString()
                }
            }
            runCatching { p.destroyForcibly() }
            (out ?: "(Termux 超时 ${timeoutSec}s)").ifBlank { "(exit ok)" }
        } catch (e: Exception) { "ERROR(termux): ${e.message}" }
    }
}
