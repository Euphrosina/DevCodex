package ai.devcodex.agent.shell

import android.content.Context
import java.io.File

/**
 * Termux 原生运行时（IQ-Code 方案，MIT）：
 * - Termux 官方 bootstrap-aarch64（32MB zip）内置 assets
 * - 首次解压到 filesDir/termux/usr + SYMLINKS.txt 符号链接 + bin 执行位
 * - ProcessBuilder 直接 exec bash（原生 bionic，无 proot/无 ptrace——限制设备可用）
 */
object TermuxRuntime {

    @Volatile
    private var ready = false

    @Volatile
    var lastError: String = ""

    fun prefix(context: Context): File = File(context.filesDir, "termux/usr")

    fun bash(context: Context): File = File(prefix(context), "bin/bash")

    fun isReady(context: Context): Boolean =
        bash(context).isFile && bash(context).canExecute()

    @Synchronized
    fun ensure(context: Context): Boolean {
        if (ready) return true
        val usr = prefix(context)
        val marker = File(usr.parentFile, ".installed")
        if (marker.exists() && isReady(context)) { ready = true; return true }
        if (marker.exists() && !isReady(context)) {
            // 半安装状态：清了重来
            File(usr.parentFile, "usr").deleteRecursively()
            marker.delete()
        }
        try {
            usr.mkdirs()
            val t0 = System.currentTimeMillis()
            // 解压 bootstrap（entries: bin/ lib/ share/ etc/ → usr/）
            val tmp = File(context.cacheDir, "termux_bootstrap.zip")
            context.assets.open("termux-bootstrap.zip").use { i ->
                tmp.outputStream().use { o -> i.copyTo(o) }
            }
            java.util.zip.ZipFile(tmp).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.name == "SYMLINKS.txt" || e.name.isBlank()) continue
                    val out = File(usr, e.name)
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        zf.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    }
                }
                // SYMLINKS.txt: 绝对路径←相对目标（Termux 前缀替换为本包路径）
                val myPrefix = usr.absolutePath
                val symlText = runCatching { zf.getInputStream(zf.getEntry("SYMLINKS.txt")).readBytes().decodeToString() }.getOrDefault("")
                symlText.lines().filter { it.contains("←") }.forEach { line ->
                    val (left, right) = line.split("←", limit = 2)
                    val linkPath = left.trim().replace("/data/data/com.termux/files/usr", myPrefix)
                    val target = right.trim()  // 相对链接父目录
                    try {
                        val lf = File(linkPath)
                        lf.parentFile?.mkdirs()
                        if (lf.exists()) lf.delete()
                        android.system.Os.symlink(target, linkPath)
                    } catch (se: Exception) { /* 容忍个别 */ }
                }
            }
            tmp.delete()
            // bin 下全部执行位（bootstrap zip 不带 mode）
            File(usr, "bin")?.takeIf { it.isDirectory }?.listFiles()?.forEach { it.setExecutable(true, false) }
            // 环境目录
            File(usr, "home").mkdirs()
            File(usr, "tmp").mkdirs()
            val ok = isReady(context)
            if (ok) marker.writeText("termux-bootstrap") else lastError = "bootstrap 自检失败（bash 缺失）"
            ready = ok
            return ok
        } catch (e: Exception) {
            lastError = "bootstrap 解压异常: ${e.message}"
            return false
        }
    }

    /** 原生执行（bash -lc，IQ-Code 同款环境变量） */
    fun exec(context: Context, command: String, cwd: String? = null, timeoutSec: Long = 30): String {
        if (!ensure(context)) return "ERROR(termux): 未安装（${lastError}）"
        val usr = prefix(context)
        val workDir = cwd?.let { File(usr, it.removePrefix("/")) }?.takeIf { it.isDirectory } ?: File(usr, "home")
        return try {
            val p = ProcessBuilder(bash(context).absolutePath, "-lc", command)
                .redirectErrorStream(true)
                .apply {
                    directory(workDir)
                    val env = environment()
                    env["HOME"] = File(usr, "home").absolutePath
                    env["PREFIX"] = usr.absolutePath
                    env["TMPDIR"] = File(usr, "tmp").absolutePath
                    env["PATH"] = File(usr, "bin").absolutePath + ":/system/bin:/system/xbin"
                    env["LD_LIBRARY_PATH"] = File(usr, "lib").absolutePath
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
