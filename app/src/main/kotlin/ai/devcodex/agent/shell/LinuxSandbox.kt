package ai.devcodex.agent.shell

import android.content.Context
import java.io.File

/**
 * 完整 Linux 沙盒（PRoot + Alpine aarch64）：
 * - proot 二进制随 jniLibs 分发（安装后位于 nativeLibraryDir，Android 保留执行权限）
 * - Alpine minirootfs 从 assets 首次解压（~15MB 展开空间）
 * - exec 走 proot -r rootfs：busybox 全套 + apk 包管理（有网时可装软件）
 */
object LinuxSandbox {

    @Volatile
    private var ready = false

    fun root(context: Context): File = File(context.filesDir, "linux").apply { mkdirs() }

    fun prootBin(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    /** 诊断信息（banner 显示失败原因） */
    @Volatile
    var lastError: String = ""

    /** proot 可用性预检（部分 ROM 限制 ptrace） */
    fun prootWorks(context: Context): Boolean {
        val bin = prootBin(context)
        if (!bin.exists()) { lastError = "proot 二进制缺失（安装异常）"; return false }
        bin.setExecutable(true, false)
        return try {
            val p = ProcessBuilder(bin.absolutePath, "--version")
                .redirectErrorStream(true)
                .apply { environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir }
                .start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val ok = p.exitValue() == 0 || out.contains("proot", ignoreCase = true)
            if (!ok) lastError = "proot 无法运行（此设备可能限制 ptrace）"
            ok
        } catch (e: Exception) { lastError = "proot 启动异常: ${e.message}"; false }
    }

    /** 首次启动解压 Alpine rootfs（幂等） */
    @Synchronized
    fun ensure(context: Context): Boolean {
        if (ready) return true
        val root = root(context)
        val marker = File(root, ".installed")
        if (!marker.exists()) {
            val fs = File(root, "alpine")
            fs.mkdirs()
            // zip 格式 rootfs（ZipInputStream 久经考验；bin 目录恢复执行位）
            context.assets.open("linux/alpine-rootfs.zip").use { input ->
                java.util.zip.ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    val buf = ByteArray(65536)
                    while (entry != null) {
                        val out = File(fs, entry.name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { o ->
                                while (true) {
                                    val n = zis.read(buf)
                                    if (n < 0) break
                                    o.write(buf, 0, n)
                                }
                            }
                            if (entry.name.startsWith("bin/") || entry.name.startsWith("usr/bin/") || entry.name.startsWith("usr/sbin/") || entry.name.startsWith("sbin/")) {
                                out.setExecutable(true, false)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            // 自检：核心文件就位才标记成功（防半解压状态）
            if (File(fs, "bin/busybox").exists() && File(fs, "bin/sh").exists()) {
                marker.writeText("alpine-3.21.3")
            }
        }
        ready = marker.exists() && prootBin(context).exists()
        return ready
    }

    /** 在完整 Linux 内执行命令（cwd 跟随，工作区挂载 /workspace） */
    fun exec(context: Context, command: String, cwd: String = "/root", timeoutSec: Long = 20): String {
        if (!ensure(context)) return "ERROR: Linux 沙盒未就绪（rootfs 解压失败或 proot 缺失）"
        val root = root(context)
        val bin = prootBin(context)
        val libDir = context.applicationInfo.nativeLibraryDir
        val cmd = arrayOf(
            bin.absolutePath,
            "-r", File(root, "alpine").absolutePath,
            "-0",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", ai.devcodex.agent.tools.BuiltinTools.workspaceRoot().absolutePath + ":/workspace",
            "-w", cwd,
            "-q", "warn",
            "/bin/sh", "-c", command,
        )
        return try {
            val p = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .apply { environment()["LD_LIBRARY_PATH"] = libDir }
                .start()
            val out = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(timeoutSec * 1000) {
                    val sb = StringBuilder()
                    p.inputStream.bufferedReader().forEachLine { line ->
                        sb.appendLine(line)
                        if (sb.length > 80_000) return@forEachLine
                    }
                    sb.toString()
                }
            }
            runCatching { p.destroyForcibly() }
            (out ?: "(Linux 超时 ${timeoutSec}s，已终止)").ifBlank { "(exit ok)" }
        } catch (e: Exception) { "ERROR(linux): ${e.message}" }
    }

    /** 带自动回退的执行：proot 失败/异常时退回 toybox 沙盒（不崩） */
    fun execSafe(context: Context, command: String, cwd: String = "/root"): String {
        val out = runCatching { exec(context, command, cwd) }.getOrElse { "ERROR: ${it.message}" }
        if (out.startsWith("ERROR")) {
            return ShellAccess.localExec(command).let { "[linux降级→toybox] $it" }
        }
        return out
    }
}

/** 极简 tar 解包器（Alpine minirootfs 均为常规文件/目录/符号链接） */
private object TarExtractor {

    private fun cstr(bytes: ByteArray, off: Int, len: Int): String {
        val end = (off until off + len).firstOrNull { bytes[it] == 0.toByte() } ?: (off + len)
        return String(bytes, off, end - off, Charsets.UTF_8)
    }

    private fun octal(bytes: ByteArray, off: Int, len: Int): Long {
        val s = cstr(bytes, off, len).trim()
        return s.toLongOrNull(8) ?: 0L
    }

    fun extract(gz: java.util.zip.GZIPInputStream, target: File) {
        val buf = ByteArray(512)
        var empty = 0
        while (true) {
            var read = 0
            while (read < 512) {
                val n = gz.read(buf, read, 512 - read)
                if (n < 0) return
                read += n
            }
            val name = cstr(buf, 0, 100)
            if (name.isBlank()) {
                empty++
                if (empty > 2) return
                continue
            }
            val size = octal(buf, 124, 12)
            val type = buf[156].toInt().toChar()
            val linkName = cstr(buf, 157, 100)
            val out = File(target, name)
            out.parentFile?.mkdirs()
            when (type) {
                '5' -> out.mkdirs()
                '2' -> {
                    if (out.exists()) out.delete()
                    runCatching {
                        java.nio.file.Files.createSymbolicLink(out.toPath(), java.nio.file.Paths.get(linkName))
                    }
                }
                '0', 0.toChar(), '7' -> {
                    var left = size
                    out.outputStream().use { o ->
                        val chunk = ByteArray(8192)
                        while (left > 0) {
                            val n = gz.read(chunk, 0, minOf(chunk.size.toLong(), left).toInt())
                            if (n < 0) break
                            o.write(chunk, 0, n)
                            left -= n
                        }
                    }
                    val pad = ((size + 511) / 512 * 512 - size).toInt()
                    var skipped = 0
                    while (skipped < pad) {
                        val n = gz.read(buf, 0, pad - skipped)
                        if (n < 0) break
                        skipped += n
                    }
                }
                else -> { /* 硬链接等：跳过数据 */ }
            }
        }
    }
}
