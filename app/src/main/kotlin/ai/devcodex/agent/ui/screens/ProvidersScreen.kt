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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import kotlinx.coroutines.launch
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
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
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
        topBar = { TopAppBar(title = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_add))
            }
        },
    ) { padding ->
        if (settings.providers.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(8.dp))
                Text(
                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_empty_desc),
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
                    // rikkahub 式提供商卡：LOGO+名称+模型数徽章+底部徽章行
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (p.isDefault) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { editing = p },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ai.devcodex.agent.ui.components.ModelLogo(p.name.ifBlank { "?" }, 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name.ifBlank { androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_unnamed) }, style = MaterialTheme.typography.titleMedium)
                                        if (p.isDefault) {
                                            Spacer(Modifier.width(6.dp))
                                            androidx.compose.material3.AssistChip(
                                                onClick = {},
                                                label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_default_chip), style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier.height(22.dp),
                                            )
                                        }
                                    }
                                    Text(p.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${p.enabledModels.size}/${p.models.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_models_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row {
                                        if (busy) CircularProgressIndicator(Modifier.size(18.dp))
                                        IconButton(onClick = { vm.setDefaultProvider(p.id) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Filled.CheckCircle, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_set_default), Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { vm.deleteProvider(p.id) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Filled.Delete, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_delete), Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
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
                title = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_preset_title)) },
                text = {
                    Column {
                        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_preset_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // 双列卡片（Operit 式）
                        LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
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
                dismissButton = { TextButton(onClick = { creating = false }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_cancel)) } },
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
    var imageModel by remember { mutableStateOf(initial.imageModel) }
    var videoModel by remember { mutableStateOf(initial.videoModel) }
    var imageEndpoint by remember { mutableStateOf(initial.imageEndpoint) }
    var videoSubmitUrl by remember { mutableStateOf(initial.videoSubmitUrl) }
    var videoStatusUrl by remember { mutableStateOf(initial.videoStatusUrl) }
    var videoTaskIdPath by remember { mutableStateOf(initial.videoTaskIdPath) }
    var videoStatusPath by remember { mutableStateOf(initial.videoStatusPath) }
    var videoResultPath by remember { mutableStateOf(initial.videoResultPath) }
    var videoSuccess by remember { mutableStateOf(initial.videoSuccessValues.joinToString(",")) }
    var videoFail by remember { mutableStateOf(initial.videoFailValues.joinToString(",")) }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Pair<String, ModelTestResult>?>(null) }
    var manualModel by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val connCtx = androidx.compose.ui.platform.LocalContext.current
    val tagImage = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tag_image)
    val tagVideo = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tag_video)
    val tagTools = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tag_tools)
    val tagNoTools = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tag_no_tools)
    val tagVision = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tag_vision)
    val tagReasoning = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tag_reasoning)
    val resUnreachable = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_unreachable)
    val resStreamOk = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_stream_ok)
    val resStreamFail = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_stream_fail)
    val resToolsOk = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tools_ok)
    val resToolsFail = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_tools_fail)
    val hostFallback = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_host_fallback)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).heightIn(max = 640.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(if (initial.name.isBlank()) androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_add) else androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_edit), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == ProviderType.OPENAI_COMPAT, onClick = { type = ProviderType.OPENAI_COMPAT; baseUrl = baseUrl.ifBlank { "https://api.openai.com/v1" } }, label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_type_openai_compat)) })
                    FilterChip(selected = type == ProviderType.ANTHROPIC, onClick = { type = ProviderType.ANTHROPIC; baseUrl = baseUrl.ifBlank { "https://api.anthropic.com" } }, label = { Text("Anthropic") })
                    FilterChip(selected = type == ProviderType.OLLAMA, onClick = { type = ProviderType.OLLAMA; baseUrl = baseUrl.ifBlank { "http://127.0.0.1:11434" } }, label = { Text("Ollama") })
                }
                // 本地模型连接提示
                Text(
                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_local_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_name_placeholder)) }, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                // 分区卡片：接入信息
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ai.devcodex.agent.ui.components.ModelLogo(name, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_access_info), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        var keyVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = apiKey, onValueChange = { apiKey = it },
                            placeholder = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_apikey_placeholder)) },
                            maxLines = if (keyVisible) 3 else 1,
                            visualTransformation = if (keyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                androidx.compose.material3.IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        if (keyVisible) androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_key_hide) else androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_key_show),
                                        Modifier.size(18.dp),
                                    )
                                }
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = baseUrl, onValueChange = { baseUrl = it },
                            placeholder = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_baseurl_placeholder)) },
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        // 连接测试（rikkahub ProviderConnectionTester 式）
                        var testResult by remember { mutableStateOf<String?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    testing = "__conn__"; testResult = null
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        testResult = runCatching {
                                            val url = baseUrl.trim().trimEnd('/') + "/models"
                                            val req = okhttp3.Request.Builder().url(url)
                                                .header("Authorization", "Bearer ${apiKey.trim()}")
                                                .build()
                                            okhttp3.OkHttpClient.Builder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                                                .newCall(req).execute().use { resp ->
                                                    if (resp.isSuccessful) connCtx.getString(ai.devcodex.agent.R.string.prov_test_ok_fmt, resp.code) else "✗ HTTP ${resp.code}"
                                                }
                                        }.getOrElse { "✗ ${it.message}" }
                                        testing = null
                                    }
                                },
                                enabled = baseUrl.isNotBlank() && testing == null,
                            ) { Text(if (testing != null) androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_testing) else androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_test_conn)) }
                            Spacer(Modifier.width(8.dp))
                            testResult?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                        }
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
                                        fetchError = connCtx.getString(ai.devcodex.agent.R.string.prov_fetch_empty)
                                    } else {
                                        models = list
                                        enabledModels = list  // 全选，用户自行取消
                                    }
                                } catch (e: Exception) {
                                    fetchError = connCtx.getString(ai.devcodex.agent.R.string.prov_fetch_failed_fmt, e.describe().take(200), normalizeBaseUrl(baseUrl))
                                }
                                fetching = false
                            }
                        },
                        enabled = !fetching && baseUrl.isNotBlank(),
                    ) {
                        if (fetching) CircularProgressIndicator(Modifier.padding(end = 6.dp))
                        Icon(Icons.Filled.Refresh, null, Modifier.padding(end = 6.dp))
                        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_auto_detect))
                    }
                }
                fetchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                // 媒体模型：生图与视频（由 Agent 工具调用，不作为聊天主模型）
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_image_model), imageModel, { imageModel = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_image_model_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_image_endpoint), imageEndpoint, { imageEndpoint = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_image_endpoint_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_video_model), videoModel, { videoModel = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_video_model_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_video_submit), videoSubmitUrl, { videoSubmitUrl = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_video_submit_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_video_status), videoStatusUrl, { videoStatusUrl = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_video_status_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_task_id_path), videoTaskIdPath, { videoTaskIdPath = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_task_id_path_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_status_path), videoStatusPath, { videoStatusPath = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_status_path_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_result_path), videoResultPath, { videoResultPath = it }, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_result_path_hint))
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_success_values), videoSuccess, { videoSuccess = it }, "succeeded,success,completed")
                        MediaField(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_media_fail_values), videoFail, { videoFail = it }, "failed,error,cancelled")
                    }
                }

                Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_models_header_fmt, models.size, enabledModels.size), style = MaterialTheme.typography.labelLarge)
                // 手动添加模型行
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = manualModel, onValueChange = { manualModel = it },
                        placeholder = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_model_manual_placeholder)) }, singleLine = true,
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
                    }) { Icon(Icons.Filled.Add, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_model_add)) }
                }
                if (models.isEmpty()) {
                    Text(
                        androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_models_empty_hint),
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
                                    when (meta.kind) {
                                        ai.devcodex.agent.provider.ModelKind.IMAGE -> add(tagImage)
                                        ai.devcodex.agent.provider.ModelKind.VIDEO -> add(tagVideo)
                                        else -> meta.tools?.let { add(if (it) tagTools else tagNoTools) }
                                    }
                                    if (meta.vision) add(tagVision)
                                    if (meta.reasoning) add(tagReasoning)
                                    meta.contextLength?.let { add(connCtx.getString(ai.devcodex.agent.R.string.prov_tag_context_fmt, it / 1000)) }
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
                                else Icon(Icons.Filled.Speed, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_test))
                            }
                            IconButton(onClick = {
                                models = models - m
                                enabledModels = enabledModels - m
                            }) { Icon(Icons.Filled.Close, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prov_remove), Modifier.size(16.dp)) }
                        }
                    }
                }
                testResult?.let { (m, r) ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(m, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text(
                                buildString {
                                    append(if (r.latencyMs >= 0) connCtx.getString(ai.devcodex.agent.R.string.prov_latency_fmt, r.latencyMs) else resUnreachable)
                                    append(if (r.streamOk) resStreamOk else resStreamFail)
                                    append(if (r.toolsOk) resToolsOk else resToolsFail)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            r.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
                        }
                    }
                }
            }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val cleaned = normalizeBaseUrl(baseUrl)
                        onSave(initial.copy(
                            name = name.ifBlank { hostOf(cleaned, hostFallback) }, type = type, baseUrl = cleaned,
                            apiKey = apiKey, models = models, enabledModels = enabledModels,
                            imageModel = imageModel.trim(), videoModel = videoModel.trim(),
                            imageEndpoint = imageEndpoint.trim(), videoSubmitUrl = videoSubmitUrl.trim(), videoStatusUrl = videoStatusUrl.trim(),
                            videoTaskIdPath = videoTaskIdPath.trim().ifBlank { "id" },
                            videoStatusPath = videoStatusPath.trim().ifBlank { "status" },
                            videoResultPath = videoResultPath.trim().ifBlank { "video_url" },
                            videoSuccessValues = videoSuccess.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { initial.videoSuccessValues },
                            videoFailValues = videoFail.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { initial.videoFailValues },
                            enabled = true,
                        ))
                    }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_save)) }
            }
        }
    }
}

@Composable
private fun MediaField(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
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
private fun hostOf(url: String, fallback: String): String = runCatching {
    java.net.URI(url).host?.removePrefix("www.")?.replaceFirstChar { it.uppercase() } ?: url
}.getOrDefault(url.ifBlank { fallback })
