package ai.devcodex.agent.shell

import java.io.File

/** Shizuku UserService 实现（运行于 Shizuku 进程，shell 权限） */
class ShellService : IShellService.Stub() {
    override fun exec(command: String): String = try {
        val p = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.ifBlank { "(exit ${p.exitValue()})" }
    } catch (e: Exception) { "ERROR: ${e.message}" }

    override fun read(path: String): String =
        File(path).takeIf { it.isFile }?.readText() ?: "ERROR: not a file: $path"

    override fun write(path: String, data: String) {
        File(path).parentFile?.mkdirs()
        File(path).writeText(data)
    }

    override fun list(path: String): String =
        File(path).takeIf { it.isDirectory }?.list()?.joinToString("\n")
            ?: "ERROR: not a dir: $path"

    override fun delete(path: String): Boolean = File(path).deleteRecursively()
}
