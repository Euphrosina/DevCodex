package ai.devcodex.agent.util

/** 异常诊断描述：永不返回 "null"，含异常类名与根因链 */
fun Throwable.describe(): String {
    val sb = StringBuilder()
    var e: Throwable? = this
    var first = true
    var depth = 0
    while (e != null && depth < 3) {
        if (!first) sb.append(" ← ")
        sb.append(e.javaClass.simpleName)
        val msg = e.message
        if (!msg.isNullOrBlank()) sb.append(": ").append(msg.take(160))
        first = false
        e = e.cause
        depth++
    }
    val s = sb.toString().ifBlank { javaClass.simpleName }
    return when {
        s.contains("SocketTimeout", ignoreCase = true) -> "$s（超时：网络不通或服务无响应）"
        s.contains("UnknownHost", ignoreCase = true) -> "$s（域名解析失败：检查 Base URL 拼写与网络）"
        s.contains("Connect", ignoreCase = true) -> "$s（连接失败：检查网络/代理/服务地址）"
        s.contains("SSL", ignoreCase = true) -> "$s（证书问题：部分代理/抓包工具会导致）"
        else -> s
    }
}
