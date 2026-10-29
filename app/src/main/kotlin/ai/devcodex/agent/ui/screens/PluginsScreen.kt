package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.devcodex.agent.AppViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(vm: AppViewModel) {
    val plugins by vm.plugins.collectAsState()
    var showInstall by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ai.devcodex.agent.data.PluginManifest?>(null) }
    val unknownAuthor = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_unknown_author)

    Scaffold(
        topBar = { TopAppBar(title = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showInstall = true }) {
                Icon(Icons.Filled.Extension, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_install))
            }
        },
    ) { padding ->
        // 开发文档大入口（首屏第一项）
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(padding).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            onClick = { showGuide = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(
                    Icons.Filled.MenuBook, null,
                    Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.padding(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_guide_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_guide_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
                Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (plugins.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_empty), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(8.dp))
                Text(
                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(plugins, key = { it.id }) { p ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (p.enabled) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { detail = p },
                    ) {
                        ListItem(
                            overlineContent = { Text("v${p.version} · ${p.author.ifBlank { unknownAuthor }}") },
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_tools_fmt, p.tools.size, p.description), maxLines = 2) },
                            leadingContent = {
                                Icon(Icons.Filled.Extension, null, Modifier.size(36.dp))
                            },
                            trailingContent = {
                                Row {
                                    Switch(
                                        checked = p.enabled,
                                        onCheckedChange = { on ->
                                            vm.pluginRepo.save(p.copy(enabled = on))
                                            vm.reloadPlugins()
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showInstall) {
        InstallPluginDialog(vm) { showInstall = false }
    }
    if (showGuide) {
        ai.devcodex.agent.ui.components.DocViewerDialog(title = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_guide_dialog), markdown = pluginDocMarkdown()) { showGuide = false }
    }

    detail?.let { p ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${p.name} v${p.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.description)
                    p.tools.forEach { t ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.padding(8.dp)) {
                                Text(t.name, style = MaterialTheme.typography.labelLarge)
                                Text("${t.endpoint.method} ${t.endpoint.url}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.pluginRepo.delete(p.id)
                    vm.reloadPlugins()
                    detail = null
                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_close)) } },
        )
    }
}

@Composable
private fun InstallPluginDialog(vm: AppViewModel, onDone: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val readFailedMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_read_failed)
    val installFailedFmt = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_install_failed_fmt)

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                }.getOrNull()
                if (content == null) error = readFailedMsg
                else install(vm, content, installFailedFmt) { error = it }
                busy = false
                if (error == null) onDone()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_install)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { filePicker.launch("*/*") }, enabled = !busy) {
                    Icon(Icons.Filled.FileOpen, null, Modifier.padding(end = 6.dp)); Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_from_file))
                }
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_from_url)) }, singleLine = true,
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val body = ai.devcodex.agent.plugin.PluginExecutor.executeRaw("""{"url":"$url","method":"GET"}""")
                                if (body.startsWith("ERROR") || body.startsWith("HTTP 4") || body.startsWith("HTTP 5")) {
                                    error = body.take(150)
                                } else {
                                    install(vm, body, installFailedFmt) { error = it }
                                    if (error == null) onDone()
                                }
                            } catch (e: Exception) { error = e.message }
                            busy = false
                        }
                    },
                    enabled = !busy && url.isNotBlank(),
                ) { Icon(Icons.Filled.Link, null, Modifier.padding(end = 6.dp)); Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_download)) }

                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_paste_json)) },
                    minLines = 3, maxLines = 6,
                )
                TextButton(
                    onClick = {
                        install(vm, text, installFailedFmt) { error = it }
                        if (error == null) onDone()
                    },
                    enabled = text.isNotBlank(),
                ) { Icon(Icons.Filled.EditNote, null, Modifier.padding(end = 6.dp)); Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.plugins_install)) }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_close)) } },
    )
}

private fun install(vm: AppViewModel, content: String, failFmt: String, onError: (String) -> Unit) {
    vm.pluginRepo.installFromText(content)
        .onSuccess { vm.reloadPlugins() }
        .onFailure { onError(failFmt.replace("%1\$s", it.message ?: "")) }
}

