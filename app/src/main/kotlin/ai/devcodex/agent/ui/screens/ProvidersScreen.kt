package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.ModelTestResult
import ai.devcodex.agent.data.ProviderConfig
import ai.devcodex.agent.data.ProviderType
import ai.devcodex.agent.provider.ProviderGateway
import ai.devcodex.agent.util.describe
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState(initial = ai.devcodex.agent.data.AppSettings())
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("API 提供商") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, "添加提供商")
            }
        },
    ) { padding ->
        if (settings.providers.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("还没有配置提供商", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(8.dp))
                Text(
                    "支持 OpenAI 兼容协议（GLM/DeepSeek/Kimi/OpenAI/OpenRouter）、Anthropic、Ollama。\n点击右下角 + 添加，填入 Base URL 和 API Key 后可自动识别模型。",
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
                items(settings.providers, key = { it.id }) { p ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (p.isDefault) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { editing = p },
                    ) {
                        ListItem(
                            overlineContent = { Text(typeName(p.type)) },
                            headlineContent = { Text(p.name.ifBlank { "未命名" }) },
                            supportingContent = {
                                Text(
                                    "${p.baseUrl}\n已启用模型 ${p.enabledModels.size}/${p.models.size}",
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { if (p.isDefault) Icon(Icons.Filled.CheckCircle, "默认") else null },
                            trailingContent = {
                                Row {
                                    if (busy) CircularProgressIndicator(Modifier.padding(8.dp))
                                    IconButton(onClick = { vm.setDefaultProvider(p.id) }) {
                                        Icon(Icons.Filled.CheckCircle, "设为默认")
                                    }
                                    IconButton(onClick = { vm.deleteProvider(p.id) }) {
                                        Icon(Icons.Filled.Delete, "删除")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        // 先选预设 → 带入配置编辑
        var presetChosen by remember { mutableStateOf<ai.devcodex.agent.data.ProviderPreset?>(null) }
        if (presetChosen == null) {
            AlertDialog(
                onDismissRequest = { creating = false },
                title = { Text("选择提供商预设") },
                text = {
                    Column {
                        Text("选一个快速开始（Base URL 已填好，只需填 Key）；或拉到底选「自定义」", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // 双列卡片（Operit 式）
                        LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(ai.devcodex.agent.data.ProviderPresets.size) { i ->
                                val preset = ai.devcodex.agent.data.ProviderPresets[i]
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { presetChosen = preset },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                ) {
                                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ai.devcodex.agent.ui.components.ModelLogo(preset.name, 44.dp)
                                        Spacer(Modifier.height(6.dp))
                                        Text(preset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            preset.desc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } },
            )
        } else {
            ProviderEditDialog(
                initial = ProviderConfig(
                    id = java.util.UUID.randomUUID().toString(),
                    name = presetChosen!!.name,
                    type = presetChosen!!.type,
                    baseUrl = presetChosen!!.baseUrl,
                ),
                onDismiss = { creating = false },
                onSave = { vm.upsertProvider(it); creating = false },
            )
        }
    }
    editing?.let { p ->
        ProviderEditDialog(
            initial = p,
            onDismiss = { editing = null },
            onSave = { vm.upsertProvider(it); editing = null },
        )
    }
}

@Composable
private fun ProviderEditDialog(
    initial: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var models by remember { mutableStateOf(initial.models) }
    var enabledModels by remember { mutableStateOf(initial.enabledModels) }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf<String?>(null) }
    var manualModel by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<Pair<String, ModelTestResult>?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "添加提供商" else "编辑提供商") },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == ProviderType.OPENAI_COMPAT, onClick = { type = ProviderType.OPENAI_COMPAT; baseUrl = baseUrl.ifBlank { "https://api.openai.com/v1" } }, label = { Text("OpenAI 兼容") })
                    FilterChip(selected = type == ProviderType.ANTHROPIC, onClick = { type = ProviderType.ANTHROPIC; baseUrl = baseUrl.ifBlank { "https://api.anthropic.com" } }, label = { Text("Anthropic") })
                    FilterChip(selected = type == ProviderType.OLLAMA, onClick = { type = ProviderType.OLLAMA; baseUrl = baseUrl.ifBlank { "http://127.0.0.1:11434" } }, label = { Text("Ollama") })
                }
                // 本地模型连接提示
                Text(
                    "本地模型：LM Studio / PocketPal / Layla 选 OpenAI 兼容，填 http://127.0.0.1:1234/v1；Ollama（含 Termux 版）选 Ollama，填 http://127.0.0.1:11434；局域网电脑主机填 http://192.168.x.x:端口",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称（如 GLM / DeepSeek）") }, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                // 分区卡片：接入信息
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ai.devcodex.agent.ui.components.ModelLogo(name, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("接入信息", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = apiKey, onValueChange = { apiKey = it },
                            label = { Text("API Key（多行支持）") },
                            maxLines = 3, minLines = 1,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = baseUrl, onValueChange = { baseUrl = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            fetching = true; fetchError = null
                            scope.launch {
                                try {
                                    val cleaned = normalizeBaseUrl(baseUrl)
                                    baseUrl = cleaned
                                    val list = ProviderGateway.listModels(initial.copy(name = name, type = type, baseUrl = cleaned, apiKey = apiKey))
                                    if (list.isEmpty()) {
                                        fetchError = "接口返回空列表，可手动添加模型"
                                    } else {
                                        models = list
                                        enabledModels = list  // 全选，用户自行取消
                                    }
                                } catch (e: Exception) {
                                    fetchError = "识别失败: ${e.describe().take(200)}，可手动在下方添加模型"
                                }
                                fetching = false
                            }
                        },
                        enabled = !fetching && baseUrl.isNotBlank(),
                    ) {
                        if (fetching) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                        Icon(Icons.Filled.Refresh, null, Modifier.padding(end = 6.dp))
                        Text("自动识别模型")
                    }
                }
                fetchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Text("模型（勾选启用，可手动添加）：", style = MaterialTheme.typography.labelLarge)
                // 手动添加模型行
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = manualModel, onValueChange = { manualModel = it },
                        label = { Text("手动输入模型名") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = {
                        val m = manualModel.trim()
                        if (m.isNotBlank() && models.none { it.equals(m, ignoreCase = true) }) {
                            models = models + m
                            enabledModels = enabledModels + m
                        }
                        manualModel = ""
                    }) { Icon(Icons.Filled.Add, "添加") }
                }
                if (models.isEmpty()) {
                    Text(
                        "尚未识别到模型 —— 点上方「自动识别」拉取，或直接手动输入模型名添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(bottom = 4.dp),
                ) {
                    items(models) { m ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = enabledModels.contains(m),
                                onCheckedChange = { on ->
                                    enabledModels = if (on) enabledModels + m else enabledModels - m
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(m, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // 能力标签（内置知识库推断）
                                val meta = ai.devcodex.agent.provider.ModelRegistry.lookup(m)
                                val tags = buildList {
                                    meta.tools?.let { add(if (it) "工具" else "无工具") }
                                    if (meta.vision) add("视觉")
                                    if (meta.reasoning) add("推理")
                                    meta.contextLength?.let { add("${it / 1000}k上下文") }
                                }
                                if (tags.isNotEmpty()) {
                                    Text(
                                        tags.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                testing = m
                                scope.launch {
                                    val r = runCatching { ProviderGateway.testModel(initial.copy(type = type, baseUrl = baseUrl, apiKey = apiKey), m) }
                                        .getOrElse { ModelTestResult(error = it.describe()) }
                                    testResult = m to r
                                    testing = null
                                }
                            }) {
                                if (testing == m) CircularProgressIndicator(Modifier.padding(4.dp))
                                else Icon(Icons.Filled.Speed, "测试")
                            }
                            IconButton(onClick = {
                                models = models - m
                                enabledModels = enabledModels - m
                            }) { Icon(Icons.Filled.Close, "移除", Modifier.size(16.dp)) }
                        }
                    }
                }
                testResult?.let { (m, r) ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(m, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text(
                                buildString {
                                    append(if (r.latencyMs >= 0) "延迟 ${r.latencyMs}ms " else "未连通 ")
                                    append(if (r.streamOk) "· 流式✓ " else "· 流式✗ ")
                                    append(if (r.toolsOk) "· 工具✓" else "· 工具✗")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            r.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleaned = normalizeBaseUrl(baseUrl)
                onSave(initial.copy(
                    name = name.ifBlank { hostOf(cleaned) }, type = type, baseUrl = cleaned,
                    apiKey = apiKey, models = models, enabledModels = enabledModels,
                    enabled = true,
                ))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun typeName(t: ProviderType): String = when (t) {
    ProviderType.OPENAI_COMPAT -> "OpenAI 兼容"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.OLLAMA -> "Ollama"
}


/** URL 清洗：补协议、去尾斜杠、去掉误粘贴的端点后缀 */
private fun normalizeBaseUrl(raw: String): String {
    var u = raw.trim()
    if (u.isBlank()) return u
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
    u = u.trimEnd('/')
    u = u.removeSuffix("/chat/completions").removeSuffix("/messages").removeSuffix("/models")
    return u
}

/** 从 URL 提取可读的提供商名 */
private fun hostOf(url: String): String = runCatching {
    java.net.URI(url).host?.removePrefix("www.")?.replaceFirstChar { it.uppercase() } ?: url
}.getOrDefault(url.ifBlank { "提供商" })
