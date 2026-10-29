package ai.devcodex.agent.shell

import android.content.Context
import java.io.File

/**
 * 内置 Ubuntu（Operit 同款方案）：PRoot 静态二进制 + Ubuntu rootfs
 * - 无需 root：PRoot 在用户空间做 syscall 翻译
 * - 首次启动解压 rootfs（assets/ubuntu-rootfs.tar.gz → filesDir/ubuntu）
 * - exec(cmd)：proot -r ubuntu 运行任意 Linux 命令（apt/python/gcc…）
 */
object PRootUbuntu {
    private var initialized = false

    fun rootfsDir(ctx: Context): File = File(ctx.filesDir, "ubuntu")

    /** 首次解压 proot 二进制 + rootfs（~80MB 解压后），进度回调 0..100 */
    fun ensure(ctx: Context, onProgress: (Int) -> Unit = {}): Boolean {
        if (initialized && rootfsDir(ctx).resolve("bin").exists()) return true
        return runCatching {
            val prootDir = File(ctx.filesDir, "proot-bin").apply { mkdirs() }
            // proot 二进制 + 依赖库（talloc/shmem/loader 全套，LD_LIBRARY_PATH 指 Termux usr/lib）
            val prootBin = File(prootDir, "proot")
            if (!prootBin.exists()) {
                ctx.assets.open("proot/proot").use { input -> prootBin.outputStream().use { input.copyTo(it) } }
                prootBin.setExecutable(true)
            }
            // proot 依赖库：Termux bootstrap 不含 talloc/shmem，从 assets 补到 termux/usr/lib
            val libDir = File(ai.devcodex.agent.shell.TermuxRuntime.prefix(ctx), "lib").apply { mkdirs() }
            for (libName in listOf("libtalloc.so.2", "libandroid-shmem.so")) {
                val dst = File(libDir, libName)
                if (!dst.exists() || dst.length() == 0L) {
                    ctx.assets.open("proot/$libName").use { input -> dst.outputStream().use { input.copyTo(it) } }
                    dst.setReadable(true, false)
                    dst.setExecutable(true, false)
                }
            }
            // rootfs（大文件解压一次）
            val root = rootfsDir(ctx)
            val readyMark = File(root, ".devcodex_ready")
            if (!readyMark.exists() || !root.resolve("bin").exists()) {
                onProgress(5)
                // 应用内 Java 层解压到临时目录，成功后原子改名，避免半成品 rootfs
                val staging = File(ctx.filesDir, "ubuntu.tmp")
                if (staging.exists()) staging.deleteRecursively()
                if (root.exists()) root.deleteRecursively()
                val result = try {
                    ctx.assets.open("ubuntu-rootfs.tar.gz").use { input ->
                        TarExtractor.extractTarGz(input, staging) { n -> onProgress(5 + minOf(85, n / 40)) }
                    }
                } catch (e: Exception) {
                    ai.devcodex.agent.util.DebugLog.e("PRoot", "rootfs 解压失败: ${e.message}", e)
                    staging.deleteRecursively()
                    throw e
                }
                ai.devcodex.agent.util.DebugLog.i("PRoot", "rootfs 解压完成 files=${result.files} dirs=${result.dirs} links=${result.links} skipped=${result.skipped}")
                if (!staging.renameTo(root)) error("rootfs 改名失败 ${staging.path} -> ${root.path}")
                onProgress(95)
                File(root, ".devcodex_ready").writeText("ok")
            }
            // resolv.conf（联网）
            runCatching {
                File(root, "etc").mkdirs()
                File(root, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            }
            initialized = true
            onProgress(100)
            root.resolve("bin").exists()
        }.getOrElse { e ->
            ai.devcodex.agent.util.DebugLog.e("PRoot", "ensure 失败: ${e.message}", e)
            false
        }
    }

    /** 在 Ubuntu 内执行命令（PRoot 用户空间） */
    fun exec(ctx: Context, command: String, timeoutSec: Int = 150): String {
        val root = rootfsDir(ctx)
        val proot = File(ctx.filesDir, "proot-bin/proot").absolutePath
        val termuxUsr = ai.devcodex.agent.shell.TermuxRuntime.prefix(ctx)
        val termuxLib = File(termuxUsr, "lib").absolutePath
        val ws = ai.devcodex.agent.tools.BuiltinTools.workspaceRoot().absolutePath
        File(root, "root/workspace").mkdirs()
        val quoted = "'" + command.replace("'", "'\\''") + "'"
        val full = "$proot -r ${root.absolutePath} " +
            "-b /dev -b /proc -b /sys -b /sdcard -b /data/local/tmp -b '$ws:/root/workspace' -b '${ctx.cacheDir.absolutePath}' " +
            "-w /root/workspace -0 /usr/bin/env HOME=/root PATH=/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin:/bin:/sbin " +
            "/bin/sh -c $quoted"
        return runCatching {
            val bash = ai.devcodex.agent.shell.TermuxRuntime.bash(ctx)
            val bashUsable = bash.isFile && File(termuxLib, "libreadline.so.8").exists()
            val prootTmp = File(ctx.cacheDir, "proot-tmp").apply { mkdirs() }
            val pb = if (bashUsable) ProcessBuilder(bash.absolutePath, "-c", full) else ProcessBuilder("sh", "-c", full)
            pb.redirectErrorStream(true).apply {
                environment()["LD_LIBRARY_PATH"] = termuxLib + ":" + (System.getenv("LD_LIBRARY_PATH") ?: "")
                environment()["PATH"] = File(termuxUsr, "bin").absolutePath + ":/system/bin:/system/xbin"
                environment()["PROOT_TMP_DIR"] = prootTmp.absolutePath
                environment()["PROOT_NO_SECCOMP"] = "1"
            }
            val p = pb.start()
            val out = StringBuilder()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(p.inputStream))
            val t = Thread { reader.forEachLine { if (out.length < 60_000) out.append(it).append('\n') } }
            t.isDaemon = true; t.start()
            val done = p.waitFor(timeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!done) { p.destroy(); return "ERROR: 超时 " + timeoutSec + "s" }
            t.join(500)
            out.toString().ifBlank { "(无输出)" }
        }.getOrElse { "ERROR: " + (it.message ?: "未知") }
    }
}
