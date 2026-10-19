package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.AppSettings
import ai.devcodex.agent.data.Message
import ai.devcodex.agent.data.ToolStatus
import ai.devcodex.agent.ui.components.ApprovalCard
import ai.devcodex.agent.ui.components.AssistantStream
import ai.devcodex.agent.ui.components.InputBar
import ai.devcodex.agent.ui.components.ToolLine
import ai.devcodex.agent.ui.components.UserLine
import ai.devcodex.agent.util.CrashHandler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mono = FontFamily.Monospace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel) {
    val session by vm.activeSession.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState(initial = AppSettings())
    val isRunning by vm.isRunning.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // 启动时检查上次崩溃
    LaunchedEffect(Unit) { crashReport = CrashHandler.latestReport(context) }

    val messages = session?.messages ?: emptyList()
    // 工具结果映射：toolCallId -> tool 消息（内联渲染用）
    val toolResults = remember(messages) {
        messages.filter { it.role == "tool" && it.toolCallId != null }.associateBy { it.toolCallId!! }
    }
    // 贴底才自动滚（防流式滚动风暴）
    val nearBottom by remember { derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
        last.index >= info.totalItemsCount - 2
    } }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && nearBottom) listState.animateScrollToItem(messages.size - 1)
    }

    val defaultProvider = settings.providers.find { it.id == session?.providerId }
        ?: settings.providers.firstOrNull { it.isDefault }
        ?: settings.providers.firstOrNull()
    val modelLabel = session?.modelId?.let { "$it · ${defaultProvider?.name ?: ""}" }
        ?: defaultProvider?.enabledModels?.firstOrNull()?.let { "$it · ${defaultProvider.name}" }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("会话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        vm.openSession(null)
                        scope.launch { drawerState.close() }
                    }) { Icon(Icons.Filled.Add, "新建") }
                }
                HorizontalDivider()
                // 修复：weight 约束高度，避免无界测量崩溃
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(sessions, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(s.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall)
                            },
                            trailingContent = {
                                IconButton(onClick = { vm.deleteSession(s.id) }) {
                                    Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp))
                                }
                            },
                            leadingContent = {
                                Box(Modifier.size(8.dp).background(
                                    if (s.id == session?.id) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ))
                            },
                            modifier = Modifier.clickable {
                                vm.openSession(s.id)
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(session?.title ?: "DevCodex", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isRunning) Text("● agent 运行中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "会话列表")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages.size, key = { messages[it].id }) { idx ->
                        val msg = messages[idx]
                        when {
                            msg.role == "user" -> UserLine(msg)
                            // tool 消息不单独渲染，结果内联到对应 assistant 的工具行
                            msg.role == "tool" -> {}
                            msg.role == "assistant" -> {
                                Column {
                                    AssistantStream(msg)
                                    // 工具行内联：⏺ + ⎿
                                    msg.toolCalls.forEach { tc ->
                                        val toolMsg = toolResults[tc.id]
                                        ToolLine(toolMsg ?: Message(
                                            id = tc.id, role = "tool", toolCallId = tc.id,
                                            toolName = tc.name, toolStatus = ToolStatus.RUNNING,
                                        ))
                                    }
                                }
                            }
                        }
                    }
                    if (pendingApproval != null) {
                        item(key = "approval") {
                            ApprovalCard(
                                reason = pendingApproval!!.first.reason,
                                toolName = pendingApproval!!.first.toolCall.name,
                                onAllow = { vm.respondApproval(granted = true, always = false) },
                                onAlwaysAllow = { vm.respondApproval(granted = true, always = true) },
                                onDeny = { vm.respondApproval(granted = false, always = false) },
                            )
                        }
                    }
                    if (messages.isEmpty()) item { EmptyHint() }
                }
                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = { vm.send(input); input = "" },
                    onStop = { vm.stop() },
                    running = isRunning,
                    modelLabel = modelLabel,
                    onPickModel = { showModelPicker = true },
                )
            }
        }
    }

    // 模型选择
    if (showModelPicker) {
        ModalBottomSheet(onDismissRequest = { showModelPicker = false }) {
            Text("选择模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            settings.providers.filter { it.enabled }.forEach { p ->
                Text(
                    "  ${p.name} · ${typeName(p.type.name)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                p.enabledModels.forEach { m ->
                    val selected = session?.modelId == m && session?.providerId == p.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.updateSessionModel(p.id, m)
                            showModelPicker = false
                        }.padding(horizontal = 8.dp),
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(m, style = MaterialTheme.typography.bodyMedium, fontFamily = Mono)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // 崩溃报告提示
    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text("检测到上次异常退出") },
            text = {
                Column {
                    Text(
                        "为了定位 bug，请把下面的堆栈发给我（已自动保存）：",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        report.take(1500),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = Mono,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(report))
                    crashReport = null
                }) { Text("复制堆栈") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashHandler.clear(context)
                    crashReport = null
                }) { Text("清除并忽略") }
            },
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier.fillMaxWidth().padding(top = 36.dp, start = 16.dp, end = 16.dp),
    ) {
        Text("DevCodex", style = MaterialTheme.typography.headlineSmall, fontFamily = Mono)
        Text("Android AI Agent · v3.1.0", style = MaterialTheme.typography.labelMedium, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        listOf(
            "工具：fs_read · shell_exec · ui_snapshot · http_request …",
            "「列出 /sdcard/Download 下的文件」",
            "「读取当前屏幕内容并总结」",
            "「写一段文字到 /sdcard/notes.txt」",
        ).forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun typeName(n: String): String = when (n) {
    "OPENAI_COMPAT" -> "OpenAI 兼容"
    "ANTHROPIC" -> "Anthropic"
    "OLLAMA" -> "Ollama"
    else -> n
}
