package ai.devcodex.agent.shell

import android.content.Context
import java.io.File

/**
 * 完整 Linux 沙盒（PRoot + 双发行版）：
 * - proot 随 jniLibs 分发（nativeLibraryDir 保留执行权限）
 * - Alpine 3.21（8MB zip，busybox/apk）+ Ubuntu 24.04.3（31MB zip，bash/apt）
 * - zip 含 unix mode（执行位）与 S_IFLNK 符号链接条目，解压时完整还原
 * - 发行版切换自动重装；proot 不可用时自动降级 toybox（execSafe）
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

    /** 首次启动解压 rootfs（幂等；统一 ZipFile 路径：执行位 + 符号链接还原） */
    @Synchronized
    fun ensure(context: Context): Boolean {
        if (ready) return true
        val root = root(context)
        val name = distroName()
        val marker = File(root, ".installed")
        val wantTag = if (name == "ubuntu") "ubuntu-24.04.3" else "alpine-3.21.3"
        // 发行版切换：标记不符则清掉重解
        if (marker.exists() && marker.readText().trim() != wantTag) {
            marker.delete()
            File(root, "alpine").deleteRecursively()
            File(root, "ubuntu").deleteRecursively()
            ready = false
        }
        if (!marker.exists()) {
            val fs = File(root, name)
            fs.mkdirs()
            val asset = if (name == "ubuntu") "linux_ubuntu.zip" else "linux/alpine-rootfs.zip"
            // 统一解压：ZipFile 读取（保 mode + 符号链接 S_IFLNK 条目）
            try {
                val tmp = File(context.cacheDir, "dc_rootfs.zip")
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
                lastError = "解压异常: ${e.message}"
            }
            // 自检：busybox（alpine）/ bash（ubuntu）+ sh 链接
            val ok = (if (name == "ubuntu") File(fs, "usr/bin/bash").exists() else File(fs, "bin/busybox").exists()) &&
                File(fs, if (name == "ubuntu") "bin/bash" else "bin/sh").exists()
            if (ok) marker.writeText(wantTag) else if (lastError.isBlank()) lastError = "自检失败（核心文件缺失）"
        }
        ready = marker.exists() && prootBin(context).exists()
        return ready
    }

    /** proot 沙盒内执行（绑定 /dev /proc /sys + 工作区） */
    fun exec(context: Context, command: String, cwd: String = "/root", timeoutSec: Long = 20): String {
        if (!ensure(context)) return "ERROR: Linux 沙盒未就绪（${lastError.ifBlank { "rootfs/proot 缺失" }}）"
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
