package ai.devcodex.agent.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import ai.devcodex.agent.ui.components.AssistantMessage
import ai.devcodex.agent.ui.components.InputBar
import ai.devcodex.agent.ui.components.ToolCard
import ai.devcodex.agent.ui.components.UserBubble
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
    var selectedMsg by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val attachPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.workspaceRepo.import(uri).onSuccess { e ->
                input = (if (input.isBlank()) "" else input + " ") + "@${e.name}"
            }
        }
    }

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
        if (messages.isNotEmpty() && nearBottom) listState.scrollToItem(messages.size - 1)
    }

    val defaultProvider = settings.providers.find { it.id == session?.providerId }
        ?: settings.providers.firstOrNull { it.isDefault }
        ?: settings.providers.firstOrNull()
    val modelLabel = session?.modelId
        ?: defaultProvider?.enabledModels?.firstOrNull()

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
                            msg.role == "user" -> Box(Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    selectedMsg = msg
                                },
                            )) { UserBubble(msg) }
                            // tool 消息内联渲染进 assistant 块
                            msg.role == "tool" -> {}
                            msg.role == "assistant" -> {
                                Row(Modifier.fillMaxWidth()) {
                                    // AI 头像
                                    Box(
                                        Modifier
                                            .padding(end = 10.dp, top = 2.dp)
                                            .size(30.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome, null,
                                            Modifier.size(17.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        if (!msg.thinking.isNullOrBlank()) ThinkingBlockInline(msg.thinking!!)
                                        if (msg.content.isBlank() && msg.toolCalls.isEmpty() && msg.thinking.isNullOrBlank()) {
                                            TypingDots()
                                        } else if (msg.content.isNotBlank()) {
                                            Box(Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = { selectedMsg = msg },
                                            )) {
                                                ai.devcodex.agent.ui.components.MarkdownText(
                                                    content = msg.content + if (msg.toolStatus == ToolStatus.RUNNING) " ▍" else "",
                                                )
                                            }
                                        }
                                        msg.toolCalls.forEach { tc ->
                                            val toolMsg = toolResults[tc.id]
                                            ToolCard(toolMsg ?: Message(
                                                id = tc.id, role = "tool", toolCallId = tc.id,
                                                toolName = tc.name, toolStatus = ToolStatus.RUNNING,
                                            ))
                                        }
                                        MessageStats(msg)
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
                    if (messages.isEmpty()) item { EmptyHint(onExample = { input = it }) }
                }
                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = { vm.send(input); input = "" },
                    onStop = { vm.stop() },
                    running = isRunning,
                    modelLabel = modelLabel,
                    onPickModel = { showModelPicker = true },
                    onAttach = { attachPicker.launch("*/*") },
                )
            }
        }
    }

    // 模型选择（aicode 风格：搜索 + 分组 + 单选）
    if (showModelPicker) {
        var query by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { showModelPicker = false }) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "选择模型",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索模型…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val activeProviders = settings.providers.filter { it.enabled && it.enabledModels.isNotEmpty() }
                if (activeProviders.isEmpty()) {
                    Text(
                        "没有可用模型，请到「提供商」页配置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    activeProviders.forEach { p ->
                        // 提供商分组头
                        item(key = "hdr-${p.id}") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    " · ${typeName(p.type.name)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val filtered = p.enabledModels.filter { it.contains(query, ignoreCase = true) }
                        items(filtered.size, key = { "${p.id}-" + filtered[it] }) { i ->
                            val m = filtered[i]
                            val selected = session?.modelId == m && session?.providerId == p.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.updateSessionModel(p.id, m)
                                        showModelPicker = false
                                    }
                                    .padding(start = 8.dp, end = 16.dp),
                            ) {
                                androidx.compose.material3.RadioButton(selected = selected, onClick = null)
                                Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                                    Text(
                                        m,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = Mono,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = ai.devcodex.agent.provider.ModelRegistry.lookup(m)
                                    val tags = buildList {
                                        meta.tools?.let { add(if (it) "工具" else "无工具") }
                                        if (meta.vision) add("视觉")
                                        if (meta.reasoning) add("推理")
                                        meta.contextLength?.let { add("${it / 1000}k") }
                                    }
                                    if (tags.isNotEmpty()) {
                                        Text(
                                            tags.joinToString("  "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 消息长按操作
    selectedMsg?.let { sm ->
        AlertDialog(
            onDismissRequest = { selectedMsg = null },
            title = { Text(if (sm.role == "user") "你的消息" else "AI 消息", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    Text(
                        sm.content.take(120).ifBlank { "（无文本内容）" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(sm.content))
                    selectedMsg = null
                }) { Text("复制全文") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteMessage(sm.id)
                    selectedMsg = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
        )
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
private fun EmptyHint(onExample: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp),
    ) {
        Text("有什么可以帮你的？", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "DevCodex v${ai.devcodex.agent.BuildConfig.VERSION_NAME} · Android AI Agent",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = Mono,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text("试试这些指令：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        listOf(
            "列出 /sdcard/Download 下的文件",
            "读取当前屏幕内容并总结",
            "写一段文字到工作区 notes.txt",
            "当前手机装了哪些 app？",
        ).forEach { line ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onExample(line) },
            ) {
                Text(
                    "❯ $line",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = Mono,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private fun typeName(n: String): String = when (n) {
    "OPENAI_COMPAT" -> "OpenAI 兼容"
    "ANTHROPIC" -> "Anthropic"
    "OLLAMA" -> "Ollama"
    else -> n
}


@Composable
private fun TypingDots() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(4.dp)))
        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(4.dp)))
        Text("思考中…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThinkingBlockInline(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(bottom = 6.dp),
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✻", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                Text(if (expanded) "思考过程" else "已深度思考", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** 消息统计行：模型 · 首包 · 速度 · 上下文 · 缓存命中 */
@Composable
private fun MessageStats(msg: Message) {
    if (msg.totalMs <= 0) return
    val speed = if (msg.totalMs > 0 && msg.outputTokens > 0) msg.outputTokens * 1000 / msg.totalMs else 0
    val ctx = msg.inputTokens + msg.outputTokens
    val cachePct = if (msg.inputTokens > 0) msg.cachedTokens * 100 / msg.inputTokens else 0
    // rikkahub 式两行统计：第一行 token 用量（输入/输出/缓存），第二行速度与耗时
    val line1 = buildList {
        if (msg.inputTokens > 0) add("输入 ${formatTok(msg.inputTokens)}")
        if (msg.outputTokens > 0) add("输出 ${formatTok(msg.outputTokens)}")
        if (msg.cachedTokens > 0) add("缓存节省 ${formatTok(msg.cachedTokens)}")
    }
    val line2 = buildList {
        msg.model?.let { add(it) }
        if (msg.firstTokenMs > 0) add("首包 ${"%.1f".format(msg.firstTokenMs / 1000.0)}s")
        if (msg.totalMs > 0) add("${"%.1f".format(msg.totalMs / 1000.0)}s")
        if (speed > 0) add("${speed} tok/s")
        if (ctx > 0) add("上下文 ${formatTok(ctx)}")
    }.filter { it.isNotBlank() }
    Column(Modifier.padding(top = 4.dp)) {
        if (line1.isNotEmpty()) Text(
            line1.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = Mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        if (line2.isNotEmpty()) Text(
            line2.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = Mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

private fun formatTok(n: Long): String = when {
    n >= 1000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}
