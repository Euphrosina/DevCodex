package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.Message
import ai.devcodex.agent.data.ToolStatus
import ai.devcodex.agent.ui.components.ApprovalCard
import ai.devcodex.agent.ui.components.AssistantText
import ai.devcodex.agent.ui.components.InputBar
import ai.devcodex.agent.ui.components.ToolCard
import ai.devcodex.agent.ui.components.UserBubble
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel) {
    val session by vm.activeSession.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState(initial = ai.devcodex.agent.data.AppSettings())
    val isRunning by vm.isRunning.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val messages = session?.messages ?: emptyList()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val defaultProvider = settings.providers.find { it.id == session?.providerId }
        ?: settings.providers.firstOrNull { it.isDefault }
        ?: settings.providers.firstOrNull()
    val modelLabel = session?.modelId?.let { m -> "$m · ${defaultProvider?.name ?: ""}" }
        ?: defaultProvider?.enabledModels?.firstOrNull()?.let { "$it · ${defaultProvider.name}" }
        ?: "未选择模型，点击设置 →"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "会话",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                IconButton(onClick = {
                    vm.openSession(null)
                    scope.launch { drawerState.close() }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("新建会话")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn {
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
                            modifier = Modifier.clickable {
                                vm.openSession(s.id)
                                scope.launch { drawerState.close() }
                            },
                            leadingContent = {
                                if (s.id == session?.id) {
                                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary))
                                }
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
                    title = { Text(session?.title ?: "DevCodex", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "会话列表")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        when {
                            msg.role == "user" -> UserBubble(msg)
                            msg.role == "tool" -> ToolCard(msg)
                            msg.role == "assistant" -> {
                                Column {
                                    AssistantText(msg)
                                    msg.toolCalls.forEach { ToolCallBrief(it.name) }
                                }
                            }
                        }
                    }
                    if (pendingApproval != null) {
                        item(key = "approval") {
                            ApprovalCard(
                                reason = pendingApproval!!.first.reason,
                                toolName = pendingApproval!!.first.toolCall.name,
                                onAllow = { vm.respondApproval(true) },
                                onDeny = { vm.respondApproval(false) },
                            )
                        }
                    }
                    if (messages.isEmpty()) {
                        item { EmptyHint() }
                    }
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

    if (showModelPicker) {
        ModalBottomSheet(onDismissRequest = { showModelPicker = false }) {
            Text("选择模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            settings.providers.filter { it.enabled }.forEach { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(p.baseUrl.ifBlank { "未配置地址" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    overlineContent = { typeName(p.type.name) },
                )
                p.enabledModels.forEach { m ->
                    val selected = session?.modelId == m && session?.providerId == p.id
                    ListItem(
                        headlineContent = { Text(m, style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            Checkbox(checked = selected, onCheckedChange = null)
                        },
                        modifier = Modifier.clickable {
                            vm.updateSessionModel(p.id, m)
                            showModelPicker = false
                        },
                    )
                }
            }
            Spacer(Modifier.padding(32.dp))
        }
    }
}

@Composable
private fun ToolCallBrief(name: String) {
    Text(
        "⚙ $name",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DevCodex v3", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.padding(8.dp))
        Text(
            "AI 智能体 · 工具调用 · 文件读写 · Shell · 界面自动化\n试试：\n「列出 /sdcard 下的文件」\n「读取屏幕内容并总结」\n「当前手机装了哪些 app」",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun typeName(n: String): String = when (n) {
    "OPENAI_COMPAT" -> "OpenAI 兼容"
    "ANTHROPIC" -> "Anthropic"
    "OLLAMA" -> "Ollama"
    else -> n
}
