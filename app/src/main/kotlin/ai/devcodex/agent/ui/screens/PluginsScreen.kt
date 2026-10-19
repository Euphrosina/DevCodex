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

    Scaffold(
        topBar = { TopAppBar(title = { Text("插件") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showInstall = true }) {
                Icon(Icons.Filled.Extension, "安装插件")
            }
        },
    ) { padding ->
        // 开发指南入口
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(padding).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp),
            ) {
                Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.padding(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("插件开发指南", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("JSON 格式说明 · 参数模板 · 示例", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                }
                TextButton(onClick = { showGuide = true }) { Text("查看") }
            }
        }
        if (plugins.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Text("暂无插件", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(8.dp))
                Text(
                    "插件 = JSON 工具包：声明工具名、参数 Schema 和 HTTP 端点模板，" +
                        "智能体即可调用你自己的服务（Home Assistant、n8n、自建 API 等）。\n" +
                        "支持从本地文件 / URL / 粘贴 JSON 三种方式安装。",
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
                            overlineContent = { Text("v${p.version} · ${p.author.ifBlank { "未知作者" }}") },
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("工具 ×${p.tools.size} · ${p.description}", maxLines = 2) },
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
        PluginGuideDialog { showGuide = false }
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
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("关闭") } },
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

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                }.getOrNull()
                if (content == null) error = "读取文件失败"
                else install(vm, content) { error = it }
                busy = false
                if (error == null) onDone()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("安装插件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { filePicker.launch("*/*") }, enabled = !busy) {
                    Icon(Icons.Filled.FileOpen, null, Modifier.padding(end = 6.dp)); Text("从文件安装")
                }
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("从 URL 安装") }, singleLine = true,
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
                                    install(vm, body) { error = it }
                                    if (error == null) onDone()
                                }
                            } catch (e: Exception) { error = e.message }
                            busy = false
                        }
                    },
                    enabled = !busy && url.isNotBlank(),
                ) { Icon(Icons.Filled.Link, null, Modifier.padding(end = 6.dp)); Text("下载") }

                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("或粘贴插件 JSON") },
                    minLines = 3, maxLines = 6,
                )
                TextButton(
                    onClick = {
                        install(vm, text) { error = it }
                        if (error == null) onDone()
                    },
                    enabled = text.isNotBlank(),
                ) { Icon(Icons.Filled.EditNote, null, Modifier.padding(end = 6.dp)); Text("安装") }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("关闭") } },
    )
}

private fun install(vm: AppViewModel, content: String, onError: (String) -> Unit) {
    vm.pluginRepo.installFromText(content)
        .onSuccess { vm.reloadPlugins() }
        .onFailure { onError("安装失败: ${it.message}") }
}

/** 插件开发指南对话框 */
@Composable
private fun PluginGuideDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("插件开发指南") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    """一个插件 = 一个 JSON 文件，声明若干"工具"。每个工具就是一次 HTTP 调用模板：AI 决定何时调用、填什么参数，DevCodex 按 URL/模板发请求并把结果回给 AI。

字段说明：
· id：插件唯一标识（英文数字横线）
· name / description：名称与描述（给用户和 AI 看）
· tools[]：工具列表
  - name：工具名（英文，AI 看到它来调用）
  - description：何时该用这个工具（写清楚，AI 靠它判断）
  - parameters：JSON Schema，声明参数类型
  - endpoint：HTTP 调用模板
    · method / url：支持 {{参数名}} 占位
    · headers：请求头（放 API Key）
    · bodyTemplate：POST 请求体模板
    · resultPath：结果提取路径（如 a.b.c，空=全文）

最小示例（汇率查询）：
{
  "id": "my-exchange",
  "name": "汇率换算",
  "description": "查汇率",
  "tools": [{
    "name": "exchange_rate",
    "description": "查询两种货币的汇率",
    "parameters": "{\"type\":\"object\",\"properties\":{\"from\":{\"type\":\"string\"},\"to\":{\"type\":\"string\"}},\"required\":[\"from\",\"to\"]}",
    "endpoint": {
      "method": "GET",
      "url": "https://open.er-api.com/v6/latest/{{from}}",
      "resultPath": "rates"
    }
  }]
}

要点：
1. parameters 是"字符串形式的 JSON Schema"，注意转义引号
2. {{参数名}} 会被 AI 提供的参数值替换（URL/headers/body 均支持）
3. API Key 放 headers 里，不要写进 URL
4. resultPath 只支持简单点路径，复杂结果留空让 AI 自己读
5. 保存为 .json 文件，从「安装 → 从文件安装」导入即可

调试：装好后直接在对话里说"查一下 USD 到 CNY 的汇率"，观察工具卡片里的参数与返回。""",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("知道了") } },
    )
}
