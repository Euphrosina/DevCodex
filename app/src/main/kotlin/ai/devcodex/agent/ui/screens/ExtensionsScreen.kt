package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.tools.BuiltinTools

/**
 * 扩展页（cccodroid devkit 式）：在线扩展清单 → 下载安装到技能/插件目录 → AI 可调用
 * 扩展源 JSON 规范：[{ "name", "type": "skill|plugin", "desc", "url", "size" }]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(vm: AppViewModel) {
    var entries by remember { mutableStateOf<List<ExtEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sourceUrl by remember { mutableStateOf("https://raw.githubusercontent.com/Moirania/DevCodex/main/extensions/index.json") }
    val scope = rememberCoroutineScope()
    val extErrEmptyResponse = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_error_empty_response)
    val extErrEmptyContent = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_error_empty_content)
    val extErrBadName = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_error_bad_name)
    val extErrTooLarge = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_error_too_large)
    val extListFailedFmt = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_error_list_failed_fmt)
    val extInstallFailedFmt = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_error_install_failed_fmt)
    val extInstalledFmt = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_installed_fmt)
    val pluginsPageLabel = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_title)
    val skillsPageLabel = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.settings_skills_title)
    fun scopeLaunch(block: suspend () -> Unit) { scope.launch { block() } }

    suspend fun load(src: String) {
        loading = true; message = null
        withContext(Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder().url(src).build()
                okhttp3.OkHttpClient.Builder().callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
                    .newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body?.string() ?: error(extErrEmptyResponse)
                        val arr = kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonArray
                            ?: return@use
                        entries = arr.mapNotNull { e ->
                            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            ExtEntry(
                                name = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "?",
                                type = (o["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "skill",
                                desc = (o["desc"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
                                url = (o["url"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
                                size = (o["size"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                            )
                        }
                    }
            }.getOrElse { message = extListFailedFmt.replace("%1\$s", it.message?.take(120) ?: "") }
        }
        loading = false
    }

    suspend fun install(entry: ExtEntry) {
        loading = true
        withContext(Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder().url(entry.url).build()
                okhttp3.OkHttpClient.Builder().callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
                    .newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val bytes = resp.body?.bytes() ?: error(extErrEmptyContent)
                        // 路径穿越防护：远程清单的 name 只允许安全文件名字符，并限制内容大小
                        if (!entry.name.matches(Regex("[A-Za-z0-9._一-龥-]+")) || entry.name == "." || entry.name == "..") error(extErrBadName)
                        if (bytes.size > 2 * 1024 * 1024) error(extErrTooLarge)
                        val ctx = BuiltinTools.appContext()
                        when (entry.type) {
                            "plugin" -> {
                                ai.devcodex.agent.data.PluginRepo(ctx).installFromText(String(bytes, Charsets.UTF_8)).getOrThrow()
                            }
                            else -> {
                                val raw = String(bytes, Charsets.UTF_8).trimStart('\uFEFF')
                                val text = if (Regex("^---\\s*\\n").containsMatchIn(raw.replace("\r\n", "\n"))) raw else "---\nname: ${entry.name}\ndescription: ${entry.desc.replace('\n', ' ')}\n---\n\n$raw"
                                ai.devcodex.agent.data.SkillRepo(ctx).install(text).getOrThrow()
                            }
                        }
                        message = extInstalledFmt.replace("%1\$s", entry.name).replace("%2\$s", if (entry.type == "plugin") pluginsPageLabel else skillsPageLabel)
                    }
            }.getOrElse { message = extInstallFailedFmt.replace("%1\$s", it.message?.take(120) ?: "") }
        }
        loading = false
    }

    LaunchedEffect(Unit) { load(sourceUrl) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_title)) },
            actions = {
                IconButton(onClick = { scopeLaunch { load(sourceUrl) } }) { Icon(Icons.Filled.Refresh, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_refresh)) }
            },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = sourceUrl, onValueChange = { sourceUrl = it },
                    label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_source_url)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = { scopeLaunch { load(sourceUrl) } }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_load)) }
            }
            Spacer(Modifier.height(8.dp))
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(6.dp))

            if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_loading), style = MaterialTheme.typography.bodySmall)
            }

            if (entries.isEmpty() && !loading) {
                Text(
                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ext_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries) { e ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (e.type == "plugin") Icons.Filled.Extension else Icons.Filled.School,
                                        null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(e.name, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.width(6.dp))
                                    Text(e.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                if (e.desc.isNotBlank()) Text(
                                    e.desc, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                )
                                if (e.size > 0) Text(
                                    "${e.size / 1024}KB · ${e.url.take(60)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.outline, maxLines = 1,
                                )
                            }
                            FilledTonalButton(onClick = { scopeLaunch { install(e) } }, enabled = !loading) {
                                Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_install))
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ExtEntry(
    val name: String,
    val type: String,
    val desc: String,
    val url: String,
    val size: Long,
)
