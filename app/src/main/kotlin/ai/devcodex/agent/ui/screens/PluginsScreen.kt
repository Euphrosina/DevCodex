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

/** 插件开发指南：全屏文档页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginGuideDialog(onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("插件开发指南") },
                    navigationIcon = { TextButton(onClick = onClose) { Text("关闭") } },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    """什么是插件

插件 = 一个 JSON 文件，给智能体添加调用外部服务的能力。AI 自动判断何时调用、自动填参数，DevCodex 按你的模板发 HTTP 请求并把结果回给 AI。

典型用途：查天气、控制智能家居（Home Assistant）、调自建 API、对接 n8n 工作流、查内部系统数据。

完整字段说明

{
  "id": "my-plugin",              // 唯一标识：英文数字横线
  "name": "我的插件",              // 显示名
  "version": "1.0.0",
  "author": "你的名字",
  "description": "一句话说明（AI 也会看）",
  "enabled": true,
  "tools": [                       // 工具列表，可多个
    {
      "name": "my_tool",           // 工具名：英文（AI 看到它来调用）
      "description": "何时该用这个工具，写得越清楚 AI 判断越准",
      "parameters": "JSON Schema 字符串（见下）",
      "endpoint": {
        "method": "GET",           // GET/POST/PUT/DELETE
        "url": "https://api.example.com/query?q={{query}}",
        "headers": { "Authorization": "Bearer YOUR_KEY" },
        "bodyTemplate": "{\"text\": \"{{text}}\"}",   // POST 请求体模板
        "resultPath": "data.items" // 结果提取：点路径；留空返回全文
      }
    }
  ]
}

parameters 写法（字符串形式的 JSON Schema）

"{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名\"}},\"required\":[\"city\"]}"

注意：整个 Schema 是字符串，内部引号要用 \" 转义。

{{参数名}} 模板语法

- url：https://wttr.in/{{city}}
- headers：值里可用
- bodyTemplate：任意位置替换
替换值来自 AI 按你的 Schema 填的参数。

五条铁律

1. API Key 放 headers，不要拼进 URL（避免泄露到日志）
2. resultPath 只支持 a.b.c 点路径；返回结构复杂就留空让 AI 自己读
3. description 写"何时用"而不是"是什么"——这是 AI 的调用依据
4. 参数的 description 一定要有，AI 靠它填对值
5. 工具名不能有空格，一个插件内不能重名

最小可用示例（可直接粘贴安装）

{
  "id": "hello-plugin",
  "name": "Hello 示例",
  "description": "演示插件结构",
  "tools": [{
    "name": "say_hello",
    "description": "向某人问好",
    "parameters": "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"对方名字\"}},\"required\":[\"name\"]}",
    "endpoint": {
      "method": "POST",
      "url": "https://httpbin.org/post",
      "bodyTemplate": "{\"greeting\": \"你好，{{name}}!\"}"
    }
  }]
}

安装与调试

1. 保存为 .json 文件 → 插件页 ➕ → 从文件安装
2. 对话里说"用 say_hello 向小明问好"
3. 展开工具卡片看：AI 填的参数、HTTP 状态、返回体
4. 修 JSON 重新导入（同名覆盖）

Skill（技能）与 Plugin（插件）的区别

· 插件 = 给 AI 新的"手"（HTTP 调用能力）
· 技能 = 给 AI 新的"脑"（知识与方法论，SKILL.md）
· 两者可配合：技能教方法，插件给工具""",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(24.dp))
            }
        }
    }
}
