package ai.devcodex.agent.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppUpdate {
    private val json = Json { ignoreUnknownKeys = true }

    data class ReleaseInfo(val tagName: String, val apkUrl: String?, val notes: String)

    /** 查 GitHub 最新 Release（Moirania/DevCodex） */
    suspend fun checkLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/Moirania/DevCodex/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .get().build()
            ai.devcodex.agent.provider.TestClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val obj = json.parseToJsonElement(resp.body?.string() ?: "").let { it as? kotlinx.serialization.json.JsonObject } ?: return@withContext null
                val tag = (obj["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return@withContext null
                val notes = (obj["body"] as? JsonPrimitive)?.contentOrNull ?: ""
                val apk = (obj["assets"] as? kotlinx.serialization.json.JsonArray)
                    ?.firstOrNull { a -> ((a as? kotlinx.serialization.json.JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull?.endsWith(".apk") == true }
                    ?.let { a -> ((a as? kotlinx.serialization.json.JsonObject)?.get("browser_download_url") as? JsonPrimitive)?.contentOrNull }
                ReleaseInfo(tag, apk, notes)
            }
        }.getOrNull()
    }

    /** 导出调试包 ZIP：崩溃日志 + 应用日志(logcat) + 会话文件 → filesDir/DevCodex-debug.zip */
    fun exportDebugZip(context: android.content.Context): File = with(context) {
        val zip = File(getExternalFilesDir(null) ?: filesDir, "DevCodex-debug.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            fun put(name: String, bytes: ByteArray) {
                runCatching {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            // 崩溃日志
            File(filesDir, "crash").listFiles()?.forEach {
                put("crash/${it.name}", it.readBytes())
            }
            // 会话
            File(filesDir, "sessions").listFiles()?.take(20)?.forEach {
                put("sessions/${it.name}", it.readBytes())
            }
            // 设置
            File(filesDir, "datastore/devcodex_settings.preferences_pb").takeIf { it.exists() }?.let {
                put("settings.pb", it.readBytes())
            }
            // logcat（应用进程）
            runCatching {
                val pid = android.os.Process.myPid()
                val log = ProcessBuilder("logcat", "-d", "--pid=$pid").redirectErrorStream(true).start()
                    .inputStream.bufferedReader().readText().take(400_000)
                put("logcat.txt", log.toByteArray())
            }
            // 设备与版本信息
            put("info.txt", buildString {
                appendLine("version: v${ai.devcodex.agent.BuildConfig.VERSION_NAME} (${ai.devcodex.agent.BuildConfig.VERSION_CODE})")
                appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            }.toByteArray())
        }
        zip
    }
}
