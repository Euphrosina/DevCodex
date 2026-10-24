package ai.devcodex.agent.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SSH 连接（执行 + 本地端口转发隧道）；JSch 维护版 */
object SshClient {

    @Volatile
    var config: SshConfig? = null

    data class SshConfig(
        val host: String,
        val port: Int = 22,
        val user: String,
        val password: String = "",
        val keyPath: String = "",     // 私钥文件路径（优先于密码）
        val passphrase: String = "",
    )

    private fun newSession(cfg: SshConfig): com.jcraft.jsch.Session =
        com.jcraft.jsch.JSch().run {
            val session = getSession(cfg.user, cfg.host, cfg.port)
            if (cfg.keyPath.isNotBlank()) {
                addIdentity(cfg.keyPath, cfg.passphrase)
            } else {
                session.setPassword(cfg.password)
            }
            session.setConfig("StrictHostKeyChecking", "accept-new")
            session.setConfig("PreferredAuthentications", "publickey,password")
            session.connect(8000)
            session
        }

    /** SSH 执行远程命令 */
    suspend fun exec(command: String, timeoutSec: Long = 20): String = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext "ERROR: SSH 未配置（设置→工具与权限→SSH 隧道）"
        runCatching {
            val session = newSession(cfg)
            try {
                val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
                channel.setCommand(command)
                channel.setErrStream(System.err)
                val out = java.io.ByteArrayOutputStream()
                channel.outputStream = out
                channel.connect((timeoutSec * 1000).toInt())
                val t0 = System.currentTimeMillis()
                while (!channel.isClosed && System.currentTimeMillis() - t0 < timeoutSec * 1000) Thread.sleep(80)
                channel.disconnect()
                out.toString("UTF-8").take(30000).ifBlank { "(exit ${channel.exitStatus})" }
            } finally {
                runCatching { session.disconnect() }
            }
        }.getOrElse { "ERROR(ssh): ${it.message}" }
    }

    /** SSH 本地端口转发隧道（如把远程内网 LLM API 转发到本地） */
    suspend fun forward(localPort: Int, remoteHost: String, remotePort: Int): String = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext "ERROR: SSH 未配置"
        runCatching {
            val session = newSession(cfg)
            // 端口转发会话保持 15 分钟（后台线程维护）
            val assigned = session.setPortForwardingL(localPort, remoteHost, remotePort)
            // 延迟断开（转发期）
            Thread {
                Thread.sleep(15 * 60 * 1000)
                runCatching { session.disconnect() }
            }.apply { isDaemon = true }.start()
            "OK: 隧道 127.0.0.1:$assigned → ${cfg.host} → $remoteHost:$remotePort（15 分钟内有效）"
        }.getOrElse { "ERROR(ssh-forward): ${it.message}" }
    }
}
