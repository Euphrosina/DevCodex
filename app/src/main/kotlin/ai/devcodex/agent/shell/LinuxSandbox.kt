package ai.devcodex.agent.shell

import android.content.Context
import java.io.File

/**
 * 完整 Linux 沙盒（PRoot + 双发行版）：
 * - proot 二进制随 jniLibs 分发（nativeLibraryDir，Android 保留执行权限）
 * - Alpine 3.21 minirootfs（~5MB zip）内置 assets/linux/
 * - Ubuntu 24.04.3 base（~31MB zip，含执行位与符号链接）内置 assets/
 * - 发行版可切换：LinuxSandbox.distro = "alpine" / "ubuntu"（切换自动重解）
 * - exec 走 proot -r rootfs：apk / apt 包管理（有网时可装软件）
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

    /** 发行版选择（"alpine" / "ubuntu"） */
    @Volatile
    var distro: String = "alpine"

    private fun distroName(): String = if (distro == "ubuntu") "ubuntu" else "alpine"

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

    /** 首次启动解压 rootfs（幂等；发行版切换自动重解） */
    @Synchronized
    fun ensure(context: Context): Boolean {
        if (ready) return true
        val root = root(context)
        val name = distroName()
        val marker = File(root, ".installed")
        val wantTag = if (name == "ubuntu") "ubuntu-24.04.3" else "alpine-3.21.3"
        // 发行版切换：标记不符则清重解
        if (marker.exists() && marker.readText().trim() != wantTag) {
            marker.delete()
            File(root, name).deleteRecursively()
            ready = false
        }
        if (!marker.exists()) {
            val fs = File(root, name)
            fs.mkdirs()
            val asset = if (name == "ubuntu") "linux_ubuntu.zip" else "linux/alpine-rootfs.zip"
            if (name == "ubuntu") {
                // Ubuntu 24.04：ZipFile 读取（保执行位 + 还原符号链接）
                try {
                    val tmp = File(context.cacheDir, "ubuntu_rootfs.zip")
                    context.assets.open(asset).use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    java.util.zip.ZipFile(tmp).use { zf ->
                        val entries = zf.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            val out = File(fs, e.name)
                            val extRaw = runCatching {
                            e.javaClass.getMethod("getExternalAttributes").invoke(e) as? Long ?: 0L
                        }.getOrDefault(0L)
                        val ext = (extRaw shr 16).toInt()
                            val isLink = (ext and 0xF000) == 0x1000
                            if (e.isDirectory) {
                                out.mkdirs()
                            } else if (isLink) {
                                val target = zf.getInputStream(e).readBytes().decodeToString()
                                out.parentFile?.mkdirs()
                                try {
                                    if (out.exists()) out.delete()
                                    android.system.Os.symlink(target, out.absolutePath)
                                } catch (se: Exception) { /* 容忍个别链接失败 */ }
                            } else {
                                out.parentFile?.mkdirs()
                                zf.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                                if (ext and 0x49 != 0) out.setExecutable(true, false)
                            }
                        }
                    }
                    tmp.delete()
                } catch (e: Exception) {
                    lastError = "Ubuntu 解压异常: ${e.message}"
                }
            } else {
                // Alpine：zip 流解压（bin 目录恢复执行位）
                try {
                    context.assets.open(asset).use { input ->
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
                } catch (e: Exception) {
                    lastError = "Alpine 解压异常: ${e.message}"
                }
            }
            // 自检：核心文件就位才标记成功（防半解压状态）
            val ok = if (name == "ubuntu") File(fs, "usr/bin/bash").exists() else File(fs, "bin/busybox").exists()
            if (ok) marker.writeText(wantTag) else if (lastError.isBlank()) lastError = "自检失败（核心文件缺失）"
        }
        ready = marker.exists() && prootBin(context).exists()
        return ready
    }

    /** 在完整 Linux 内执行命令（cwd 跟随，工作区挂载 /workspace） */
    fun exec(context: Context, command: String, cwd: String = "/root", timeoutSec: Long = 20): String {
        if (!ensure(context)) return "ERROR: Linux 沙盒未就绪（rootfs 解压失败或 proot 缺陷，${lastError}）"
        val root = root(context)
        val dname = distroName()
        val shellBin = if (dname == "ubuntu") "/bin/bash" else "/bin/sh"
        val bin = prootBin(context)
        val libDir = context.applicationInfo.nativeLibraryDir
        val cmd = arrayOf(
            bin.absolutePath,
            "-r", File(root, dname).absolutePath,
            "-0",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", ai.devcodex.agent.tools.BuiltinTools.workspaceRoot().absolutePath + ":/workspace",
            "-w", cwd,
            "-q", "warn",
            shellBin, "-c", command,
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
