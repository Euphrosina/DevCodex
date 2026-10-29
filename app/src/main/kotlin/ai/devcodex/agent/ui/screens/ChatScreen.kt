package ai.devcodex.agent.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Dvr
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.ChatSession
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
    val cmdCtx = androidx.compose.ui.platform.LocalContext.current
    val session by vm.activeSession.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val settings by vm.settings.collectAsState(initial = AppSettings())
    LaunchedEffect(settings.theme.codeTheme) { ai.devcodex.agent.m3l.CodeThemeHolder.current = settings.theme.codeTheme }
    val isRunning by vm.isRunning.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var showCharacters by remember { mutableStateOf(false) }
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
    val lastThinkingLen = messages.lastOrNull()?.thinking?.length ?: 0
    // 位置记忆：会话切换时恢复上次查看位置
    val sessionKey = session?.id ?: "new"
    LaunchedEffect(sessionKey) {
        val saved = vm.chatScrollMap[sessionKey] ?: -1
        if (saved >= 0 && saved < messages.size) {
            runCatching { listState.scrollToItem(saved) }
        } else if (messages.isNotEmpty()) {
            runCatching { listState.scrollToItem(messages.lastIndex) }
        }
    }
    // 离开/滚动时记录位置
    LaunchedEffect(listState.firstVisibleItemIndex, sessionKey) {
        vm.chatScrollMap[sessionKey] = listState.firstVisibleItemIndex
    }
    // 思考期间不再自动滚动（根治回弹：思考文本增长不拉底，新消息仍跟随）
    LaunchedEffect(messages.size, isRunning) {
        if (isRunning && messages.isNotEmpty() &&
            listState.firstVisibleItemIndex >= (messages.size - 2).coerceAtLeast(0)) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }
    // 自动滚动：新消息（发送/AI 输出）时跟随到底
    LaunchedEffect(messages.size, isRunning) {
        if (messages.isNotEmpty() && isRunning && listState.firstVisibleItemIndex >= messages.size - 3) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }
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
        ?: defaultProvider?.enabledModels?.firstOrNull { ai.devcodex.agent.provider.ModelRegistry.isChatModel(it) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_drawer_sessions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        vm.openSession(null)
                        scope.launch { drawerState.close() }
                    }) { Icon(Icons.Filled.Add, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_drawer_new)) }
                }
                HorizontalDivider()
                // 角色卡（Operit 式）：列表 + JSON 导入 + 点击开聊
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_drawer_characters), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showCharacters = true }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_drawer_characters_manage)) }
                }
                HorizontalDivider()
                // 会话列表（重设计）：日期分组 + 最后消息预览 + 相对时间 + 选中卡
                val timeJustNow = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_time_just_now)
                val groupToday = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_group_today)
                val groupYesterday = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_group_yesterday)
                val groupWeek = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_group_week)
                val groupEarlier = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_group_earlier)
                val resCtx = androidx.compose.ui.platform.LocalContext.current
                fun relTime(ts: Long): String {
                    val diff = (System.currentTimeMillis() - ts) / 1000
                    return when {
                        diff < 60 -> timeJustNow
                        diff < 3600 -> resCtx.getString(ai.devcodex.agent.R.string.chat_time_minutes_ago, (diff / 60).toInt())
                        diff < 86400 -> resCtx.getString(ai.devcodex.agent.R.string.chat_time_hours_ago, (diff / 3600).toInt())
                        diff < 86400 * 7 -> resCtx.getString(ai.devcodex.agent.R.string.chat_time_days_ago, (diff / 86400).toInt())
                        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))
                    }
                }
                fun groupOf(ts: Long): String {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
                    val today = cal.timeInMillis
                    return when {
                        ts >= today -> groupToday
                        ts >= today - 86400000L -> groupYesterday
                        ts >= today - 86400000L * 7 -> groupWeek
                        else -> groupEarlier
                    }
                }
                val grouped = sessions.sortedByDescending { it.updatedAt }.groupBy { groupOf(it.updatedAt) }
                LazyColumn(
                    Modifier.widthIn(max = 760.dp).weight(1f, fill = false),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),  // Aether 消息间距
                ) {
                    grouped.forEach { (group, items) ->
                        item(key = "grp-$group") {
                            Text(
                                group,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
                            )
                        }
                        items(items, key = { it.id }) { s ->
                            val selected = s.id == session?.id
                            val preview = s.messages.lastOrNull { it.content.isNotBlank() }?.content?.replace('\n', ' ') ?: ""
                            Surface(
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                                    .clickable {
                                        vm.openSession(s.id)
                                        scope.launch { drawerState.close() }
                                    },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                s.title.ifBlank { androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_session_untitled) },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                relTime(s.updatedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (preview.isNotBlank()) {
                                            Text(
                                                preview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    IconButton(onClick = { vm.deleteSession(s.id) }) {
                                        Icon(Icons.Filled.Delete, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_delete), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column(Modifier.clickable { showModelPicker = true }) {
                            // MChat 式：会话标题 + 当前模型行（Logo+名称+下拉箭头）
                            Text(session?.title?.ifBlank { androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_session_untitled) } ?: "DevCodex", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val displayModel = session?.modelId ?: settings.providers.firstOrNull { it.enabled }?.enabledModels?.firstOrNull { ai.devcodex.agent.provider.ModelRegistry.isChatModel(it) } ?: androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_model_pick)
                                ai.devcodex.agent.ui.components.ModelLogo(displayModel, 16.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    displayModel.substringAfterLast('/'),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = Mono,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp),
                                )
                                Icon(Icons.Filled.ArrowDropDown, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_switch), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (isRunning) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_sessions_list))
                        }
                    },
                    actions = {
                        // 工作区直达（沉浸模式可达）
                        IconButton(onClick = { vm.navRequest.value = "workspace" }) {
                            Icon(Icons.Filled.Folder, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_workspace))
                        }
                        // 沉浸模式下的设置出口（任何时候都可进）
                        IconButton(onClick = { vm.navRequest.value = "settings" }) {
                            Icon(Icons.Filled.Settings, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_settings))
                        }
                        // 次要入口收进溢出菜单：提供商、压缩会话、终端
                        var showMore by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMore = true }) {
                                Icon(Icons.Filled.MoreVert, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_more))
                            }
                            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_menu_providers)) },
                                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) },
                                    onClick = { showMore = false; vm.navRequest.value = "providers" },
                                )
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_menu_compact)) },
                                    leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                                    onClick = { showMore = false; vm.compactSessionNow() },
                                )
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_menu_terminal)) },
                                    leadingIcon = { Icon(Icons.Filled.Dvr, null) },
                                    onClick = { showMore = false; vm.navRequest.value = "terminal" },
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            // Aether 全屏渐变（浅色：白→FAFAFA→F2F2F5 / 深色：1B1D22→151619→1C1F23）
            // pure 方案：colorScheme 动态渐变（任何主题自动适配）
            Column(
                Modifier.fillMaxSize().padding(padding).background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                ),
            ) {
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
                        val busyResendMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_busy_resend)
                        val busyRewindMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_busy_rewind)
                        val busyDeleteMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_busy_delete)
                        val toastCtx = androidx.compose.ui.platform.LocalContext.current
                        when {
                            msg.role == "user" -> Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Spacer(Modifier.weight(1f))
                                // cccodroid：用户消息=右对齐列（气泡+操作栏上下排）
                                Column(horizontalAlignment = Alignment.End) {
                                    Box(
                                        Modifier
                                            .widthIn(max = 340.dp)
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { selectedMsg = msg },
                                            )
                                    ) { UserBubble(msg) }
                                    MsgActionBar(
                                        msg,
                                        onCopy = { c -> clipboard.setText(AnnotatedString(c)); vm.toast.value = toastCtx.getString(ai.devcodex.agent.R.string.chat_copied_fmt, c.length) },
                                        onResend = { if (msg.content.isNotBlank()) { if (isRunning) vm.toast.value = busyResendMsg else { vm.rewindTo(msg.id); vm.send(msg.content) } } },
                                        onRewind = { if (isRunning) vm.toast.value = busyRewindMsg else vm.rewindTo(msg.id) },
                                        onDelete = { if (isRunning) vm.stop(); vm.deleteMessage(msg.id) },
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                if (settings.theme.showAvatar && settings.theme.showNick) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
                                    ai.devcodex.agent.ui.components.Avatar(
                                        name = settings.theme.userNick,
                                        avatarFile = settings.theme.userAvatar,
                                        defaultColor = androidx.compose.ui.graphics.Color(0xFF059669),
                                        size = 24.dp,
                                    )
                                    Text(settings.theme.userNick.ifBlank { androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_user_nick_default) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else if (settings.theme.showAvatar) ai.devcodex.agent.ui.components.Avatar(
                                    name = settings.theme.userNick,
                                    avatarFile = settings.theme.userAvatar,
                                    defaultColor = androidx.compose.ui.graphics.Color(0xFF059669),
                                    size = 24.dp,
                                )
                            }
                            // tool 消息内联渲染进 assistant 块
                            msg.role == "tool" -> {}
                            msg.role == "assistant" -> {
                                Row(Modifier.fillMaxWidth()) {
                                    if (settings.theme.showAvatar && settings.theme.showNick) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
                                        ai.devcodex.agent.ui.components.Avatar(
                                            name = settings.theme.aiNick,
                                            avatarFile = settings.theme.aiAvatar,
                                            defaultColor = androidx.compose.ui.graphics.Color(0xFF4F46E5),
                                            defaultIcon = Icons.Filled.AutoAwesome,
                                            size = 24.dp,
                                        )
                                        Text(settings.theme.aiNick.ifBlank { "DevCodex" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    } else if (settings.theme.showAvatar) ai.devcodex.agent.ui.components.Avatar(
                                        name = settings.theme.aiNick,
                                        avatarFile = settings.theme.aiAvatar,
                                        defaultColor = androidx.compose.ui.graphics.Color(0xFF4F46E5),
                                        defaultIcon = Icons.Filled.AutoAwesome,
                                        size = 24.dp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        if (!msg.thinking.isNullOrBlank()) ThinkingBlockInline(msg.thinking!!, settings.theme.autoCollapseThinking, msg.thinkingMs, isRunning && idx == messages.lastIndex && msg.content.isBlank() && msg.toolCalls.isEmpty())
                                        if (msg.content.isBlank() && msg.toolCalls.isEmpty() && msg.thinking.isNullOrBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TypingDots()
                                                // MonkeyCode 式流式定位：段落/字符位置
                                                val paras = msg.content.split("\n\n").size
                                                val chars = msg.content.length
                                                if (chars > 0) Text(
                                                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_streaming_pos_fmt, paras, chars),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (!msg.thinking.isNullOrBlank()) Text(
                                                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_streaming_thinking_fmt, msg.thinking!!.length),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                )
                                            }
                                        } else if (msg.content.isBlank() && msg.toolCalls.isEmpty() && !msg.thinking.isNullOrBlank()) {
                                            // 纯思考消息：不渲染空正文（修"（空）"占位）
                                        } else if (msg.content.isNotBlank()) {
                                            // 性能关键：流式输出中（最后一条+运行中）用轻量纯 Text，
                                            // 完成后一次性切换 M3L 富渲染（Markdown/代码高亮/LaTeX/表格）
                                            val streamingNow = isRunning && idx == messages.lastIndex && msg.role == "assistant"
                                            Box(Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = { selectedMsg = msg },
                                            )) {
                                                if (false && streamingNow) {
                                                    Text("", Modifier.fillMaxWidth())
                                                } else if (false) {
                                                } else Column(Modifier.fillMaxWidth()) {
                                                    // Gemini 式流体：流式尾部 30 字符渐显 + ▍；完成后走 M3L 富渲染
                                                    if (false && streamingNow) {
                                                        val full = msg.content
                                                        val head = full.dropLast(24)
                                                        val tail = full.takeLast(24)
                                                        if (head.isNotEmpty()) Text(head, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                                                        // Gemini 式流体：尾部逐字渐显（alpha 动画）+呼吸光标
                                                        val fluidAlpha by androidx.compose.animation.core.animateFloatAsState(
                                                            targetValue = if (tail.isNotEmpty()) 1f else 0f,
                                                            animationSpec = androidx.compose.animation.core.tween(300),
                                                            label = "fluid",
                                                        )
                                                        Text(tail + " ▍", Modifier.fillMaxWidth().alpha(0.3f + 0.7f * fluidAlpha), style = MaterialTheme.typography.bodyLarge)
                                                    } else {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                                    androidx.compose.runtime.CompositionLocalProvider(
                                                    ai.devcodex.agent.m3l.LocalCodeTheme provides settings.theme.codeTheme,
                                                    ai.devcodex.agent.m3l.LocalCodeBlockDark provides (MaterialTheme.colorScheme.background.luminance() < 0.5f),
                                                        ai.devcodex.agent.m3l.LocalMarkdownRendererTheme provides ai.devcodex.agent.m3l.MarkdownRendererTheme(
                                                            primary = MaterialTheme.colorScheme.primary,
                                                            surface = MaterialTheme.colorScheme.surfaceContainerLow,
                                                            outlineVariant = MaterialTheme.colorScheme.outlineVariant,
                                                        ),
                                                        ai.devcodex.agent.m3l.LocalCodeBlockHeader provides { block ->
                                                            // 代码块工具条：复制按钮
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                                                            ) {
                                                                Text(
                                                                    block.language.ifBlank { "code" },
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontFamily = Mono,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.weight(1f),
                                                                )
                                                                TextButton(onClick = {
                                                                    clipboard.setText(AnnotatedString(block.code))
                                                                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_copy), style = MaterialTheme.typography.labelSmall) }
                                                            }
                                                        },
                                                    ) {
                                                        ai.devcodex.agent.m3l.MixedMarkdownLatex(
                                                            text = msg.content,
                                                            isStreaming = streamingNow,
                                                            textColor = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    }
                                                }
                                                    }

                                            }
                                        }
                                        msg.toolCalls.forEach { tc ->
                                            val toolMsg = toolResults[tc.id]
                                            ToolCard(
                                                msg = toolMsg ?: Message(
                                                    id = tc.id, role = "tool", toolCallId = tc.id,
                                                    toolName = tc.name, toolStatus = ToolStatus.PENDING,
                                                ),
                                                argsJson = tc.argsJson,
                                                live = isRunning,
                                            )
                                            val pa = pendingApproval
                                            if (pa != null && pa.first.toolCall.id == tc.id) {
                                                ApprovalCard(
                                                    reason = pa.first.reason,
                                                    toolName = tc.name,
                                                    argsJson = tc.argsJson,
                                                    onAllow = { vm.respondApproval(granted = true, always = false) },
                                                    onAlwaysAllow = { vm.respondApproval(granted = true, always = true) },
                                                    onDeny = { vm.respondApproval(granted = false, always = false) },
                                                )
                                            }
                                        }
                                        }
                                        MessageStats(msg, session)
                                        // cccodroid：AI 消息底部操作栏（复制/回退/删除）
                                        MsgActionBar(
                                            msg,
                                            onCopy = { c -> clipboard.setText(AnnotatedString(c)); vm.toast.value = toastCtx.getString(ai.devcodex.agent.R.string.chat_copied_fmt, c.length) },
                                            onResend = {},
                                            onRewind = { if (isRunning) vm.toast.value = busyRewindMsg else vm.rewindTo(msg.id) },
                                            onDelete = { if (isRunning) { vm.toast.value = busyDeleteMsg } else vm.deleteMessage(msg.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (messages.isEmpty()) item { EmptyHint(onExample = { input = it }) }
                }
                val curModelId = session?.modelId ?: settings.providers.firstOrNull { it.enabled }?.enabledModels?.firstOrNull { ai.devcodex.agent.provider.ModelRegistry.isChatModel(it) } ?: ""
                val modelMaxCtx2 = ai.devcodex.agent.provider.ModelRegistry.lookup(curModelId).contextLength ?: Int.MAX_VALUE
                // 斜杠命令建议浮层（输入以 / 开头）
                if (input.startsWith("/") && input.length <= 24 && !input.contains(" ")) {
                    val skills = remember { runCatching { ai.devcodex.agent.data.SkillRepo(cmdCtx).list() }.getOrDefault(emptyList()) }
                    val cmds = SlashCommands.definitions(cmdCtx).map { it.name to it.desc } +
                        skills.map { "/${it.name}" to it.description }
                    val matches = cmds.filter { it.first.startsWith(input.removePrefix("/").take(2).ifBlank { "" }.let { p -> "/" + p }) || it.first.startsWith(input) }.take(8)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    ) {
                        Column {
                            matches.forEach { (name, desc) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { input = name + " " }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontFamily = Mono)
                                    Spacer(Modifier.width(10.dp))
                                    Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = {
                        if (input.startsWith("/") && SlashCommands.execute(vm, input, { showModelPicker = true }, { }, { cmdCtx })) {
                            input = ""
                        } else {
                            vm.send(input); input = ""
                        }
                    },
                    onStop = { vm.stop() },
                    running = isRunning,
                    modelId = curModelId,
                    modelLabel = modelLabel,
                    curCtx = session?.contextOverride ?: vm.pendingContext,
                    effort = settings.reasoningEffort,
                    modelMaxCtx = modelMaxCtx2,
                    onCtx = { vm.setSessionContext(it) },
                    onCompact = { vm.compactSessionNow() },
                    usageOutTokens = (messages.lastOrNull()?.outputTokens ?: 0L).toInt(),
                    ctxUsedPct = run {
                        val ctxMax = (session?.contextOverride ?: 0).takeIf { it > 0 } ?: ai.devcodex.agent.provider.ModelRegistry.lookup(curModelId).contextLength ?: 128_000
                        val est = (messages.sumOf { (it.content.length / 2.5).toLong() + it.toolCalls.size * 50 }) * 100 / ctxMax
                        est.toInt().coerceIn(0, 100)
                    },
                    onEffort = { vm.saveSettings { s2 -> s2.copy(reasoningEffort = it) } },
                    onPickModel = { showModelPicker = true },
                    onAttach = { attachPicker.launch("*/*") },
                )
            }
        }
    }

    // 角色卡 BottomSheet
    if (showCharacters) {
        val chars = androidx.compose.runtime.remember { ai.devcodex.agent.data.CharacterRepo.list() }
        val charImportOkMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_import_ok)
        val charImportFailMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_import_fail)
        val charUpdatedMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_updated)
        val charUpdateFailedMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_update_failed)
        val charDeletedMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_deleted)
        val charPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                runCatching {
                    val text = ai.devcodex.agent.tools.BuiltinTools.appContext().contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    if (text.isNotBlank()) {
                        if (vm.importCharacter(text)) vm.toast.value = charImportOkMsg
                        else vm.toast.value = charImportFailMsg
                    }
                }
            }
        }
        ModalBottomSheet(onDismissRequest = { showCharacters = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_drawer_characters), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.OutlinedButton(onClick = { charPicker.launch("application/json") }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_import_json), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (chars.isEmpty()) {
                    Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_empty),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                chars.forEach { c ->
                    var editThis by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    if (editThis) {
                        var eName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.name) }
                        var eDesc by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.description) }
                        var ePers by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.personality) }
                        var eFirst by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.firstMes) }
                        var eAvatar by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.avatar) }
                        val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.GetContent()
                        ) { uri: android.net.Uri? ->
                            if (uri != null) {
                                runCatching {
                                    val ctx2 = ai.devcodex.agent.tools.BuiltinTools.appContext()
                                    val tmp = java.io.File(ctx2.cacheDir, "avatar_tmp")
                                    ctx2.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                                    eAvatar = ai.devcodex.agent.data.CharacterRepo.importAvatar(eName, tmp) ?: eAvatar
                                    tmp.delete()
                                }
                            }
                        }
                        AlertDialog(
                            onDismissRequest = { editThis = false },
                            title = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_edit_title)) },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    OutlinedTextField(value = eName, onValueChange = { eName = it }, label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_name)) }, singleLine = true)
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(value = eDesc, onValueChange = { eDesc = it }, label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_desc)) }, minLines = 2)
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(value = ePers, onValueChange = { ePers = it }, label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_personality)) })
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(value = eFirst, onValueChange = { eFirst = it }, label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_first_mes)) }, minLines = 2)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = { avatarPicker.launch("image/*") }) {
                                        Icon(Icons.Filled.Add, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (eAvatar.isBlank()) androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_avatar_pick) else androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_avatar_picked_fmt, eAvatar.take(16)), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val updated = c.copy(name = eName.trim(), description = eDesc, personality = ePers, firstMes = eFirst, avatar = eAvatar)
                                    if (vm.updateCharacter(c, updated)) { showCharacters = false; vm.toast.value = charUpdatedMsg }
                                    else vm.toast.value = charUpdateFailedMsg
                                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_save)) }
                            },
                            dismissButton = { TextButton(onClick = { editThis = false }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_cancel)) } },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.startWithCharacter(c)
                            showCharacters = false
                            scope.launch { drawerState.close() }
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 头像：导入图片优先，无则首字母
                        val avFile = remember(c) { ai.devcodex.agent.data.CharacterRepo.avatarFile(c) }
                        if (avFile != null) {
                            val avBmp = remember(c) { android.graphics.BitmapFactory.decodeFile(avFile.absolutePath) }
                            if (avBmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = avBmp.asImageBitmap(), contentDescription = c.name,
                                    modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                )
                            } else CharAvatarFallback(c)
                        } else CharAvatarFallback(c)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                c.description.ifBlank { androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_no_desc) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { editThis = true }) { Icon(Icons.Filled.Schedule, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_edit), Modifier.size(16.dp)) }
                        IconButton(onClick = {
                            ai.devcodex.agent.data.CharacterRepo.delete(c.name)
                            showCharacters = false
                            vm.toast.value = charDeletedMsg
                        }) { Icon(Icons.Filled.Delete, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_delete), Modifier.size(16.dp)) }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
            }
        }
    }

    // 模型选择（MChat 式：紧凑单行列表 + 选中指示 + 分组小字头）
    if (showModelPicker) {
        var query by remember { mutableStateOf("") }
        // 修：输入中打开 sheet 先清焦点（键盘不再挤压列表/拦截点击）
        val focusMgr = androidx.compose.ui.platform.LocalFocusManager.current
        androidx.compose.runtime.LaunchedEffect(showModelPicker) {
            if (showModelPicker) runCatching { focusMgr.clearFocus(force = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_model_pick),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_model_search)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                val activeProviders = settings.providers.filter { p ->
                    p.enabled && p.enabledModels.any { ai.devcodex.agent.provider.ModelRegistry.isChatModel(it) }
                }
                if (activeProviders.isEmpty()) {
                    Text(
                        androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_model_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 2.dp, bottom = 20.dp),
                ) {
                    activeProviders.forEach { p ->
                        item(key = "hdr-${p.id}") {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        val filtered = p.enabledModels.filter {
                            ai.devcodex.agent.provider.ModelRegistry.isChatModel(it) && it.contains(query, ignoreCase = true)
                        }
                        items(filtered.size, key = { p.id + '-' + filtered[it] + '-' + it }) { i ->
                            val m = filtered[i]
                            val selected = session?.modelId == m && session?.providerId == p.id
                            // Codex 风格行：模型名 + 灰色能力小字 + 右侧勾；选中后下方展开上下文档位
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        vm.updateSessionModel(p.id, m)
                                        showModelPicker = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ai.devcodex.agent.ui.components.ModelLogo(m, 28.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        m,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = Mono,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = ai.devcodex.agent.provider.ModelRegistry.lookup(m)
                                    val badgeTools = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_badge_tools)
                                    val badgeVision = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_badge_vision)
                                    val badgeReasoning = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_badge_reasoning)
                                    val badgeCustom = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_badge_custom)
                                    val badges = buildList {
                                        meta.tools?.let { if (it) add(badgeTools to 0xFF1A73E8) }
                                        if (meta.vision) add(badgeVision to 0xFF8E24AA)
                                        if (meta.reasoning) add(badgeReasoning to 0xFFE8710A)
                                        meta.contextLength?.let { add("ctx " + (it / 1000) + "k" to 0xFF5F6368) }
                                        if (meta === ai.devcodex.agent.provider.ModelMeta.Companion.UNKNOWN) add(badgeCustom to 0xFF5F6368)
                                    }
                                    // 徽章单行限宽（防重叠/溢出）
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                    ) {
                                        badges.forEach { (t, _) ->
                                            Box(
                                                Modifier
                                                    .padding(end = 6.dp, top = 1.dp, bottom = 1.dp)
                                            ) {
                                                Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                }
                                if (selected) {
                                    Icon(Icons.Filled.CheckCircle, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_selected), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                                // 选中模型：上下文窗口档位（检测模型支持上限），位于模型行下方
                                if (selected) {
                                    val modelMax = ai.devcodex.agent.provider.ModelRegistry.lookup(m).contextLength ?: Int.MAX_VALUE
                                    val curCtx = session?.contextOverride ?: 0
                                    val options = listOf(0, 131072, 262144, 524288, 1048576, 2097152)
                                    val labels = mapOf(0 to androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_ctx_default_fmt, modelMax / 1024), 131072 to "128k", 262144 to "256k", 524288 to "512k", 1048576 to "1M", 2097152 to "2M")
                                    androidx.compose.foundation.layout.FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth().padding(start = 38.dp, top = 2.dp, bottom = 6.dp),
                                    ) {
                                        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_ctx_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                                        options.forEach { opt ->
                                            val support = opt == 0 || opt <= modelMax
                                            FilterChip(
                                                selected = curCtx == opt,
                                                onClick = { if (support) vm.setSessionContext(opt) },
                                                label = { Text(labels[opt] ?: "?", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                                enabled = support,
                                                modifier = Modifier.height(24.dp),
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
    }

    // 消息长按操作
    selectedMsg?.let { sm ->
        AlertDialog(
            onDismissRequest = { selectedMsg = null },
            title = { Text(if (sm.role == "user") androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_msg_yours) else androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_msg_ai), style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    Text(
                        sm.content.take(120).ifBlank { androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_msg_no_text) },
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
                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_msg_copy_all)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (isRunning) vm.stop()
                    vm.deleteMessage(sm.id)
                    selectedMsg = null
                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    // 崩溃报告提示
    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_crash_title)) },
            text = {
                Column {
                    Text(
                        androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_crash_hint),
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
                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_crash_copy)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashHandler.clear(context)
                    crashReport = null
                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_crash_clear)) }
            },
        )
    }
}

@Composable
private fun EmptyHint(onExample: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp),
    ) {
        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "DevCodex v${ai.devcodex.agent.BuildConfig.VERSION_NAME} · Android AI Agent",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = Mono,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_empty_try), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        listOf(
            androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_example_1),
            androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_example_2),
            androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_example_3),
            androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_example_4),
        ).forEach { line ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
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
        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_typing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThinkingBlockInline(text: String, autoCollapse: Boolean = true, thinkingMs: Long = 0, streaming: Boolean = false) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(autoCollapse) { mutableStateOf(!autoCollapse) }
    var wasStreaming by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(streaming) }
    // 仅在 streaming 状态发生跳变时自动展开/收起，首次组合与列表回收不重置用户手动操作
    androidx.compose.runtime.LaunchedEffect(streaming) {
        if (streaming) expanded = true
        else if (wasStreaming && autoCollapse) expanded = false
        wasStreaming = streaming
    }
    // Operit 渐变思考：呼吸流光渐变（tertiary→primary→secondary）
    val c1 = MaterialTheme.colorScheme.tertiary
    val c2 = MaterialTheme.colorScheme.primary
    val c3 = MaterialTheme.colorScheme.secondary
    val breath = rememberInfiniteTransition(label = "think")
    val alpha by breath.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breathe",
    )
    val offset by breath.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "flow",
    )
    val flowBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(c1, c2, c3, c1),
        start = androidx.compose.ui.geometry.Offset(offset - 600f, 0f),
        end = androidx.compose.ui.geometry.Offset(offset, 0f),
    )
    val thinkingActiveLabel = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_thinking_active)
    val thinkingProcessLabel = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_thinking_process)
    val thinkingDoneLabel = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_thinking_done)
    val thinkingCtx = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(bottom = 6.dp).clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 2.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✻", style = MaterialTheme.typography.labelLarge, color = c1.copy(alpha = alpha))
                Text(
                    buildString {
                        if (streaming) append(thinkingActiveLabel) else if (expanded) append(thinkingProcessLabel) else append(thinkingDoneLabel)
                        if (!streaming && thinkingMs > 0) {
                            val sec = thinkingMs / 1000.0
                            append(if (sec >= 60) thinkingCtx.getString(ai.devcodex.agent.R.string.chat_thinking_dur_min_fmt, sec.toInt() / 60, sec.toInt() % 60)
                                   else thinkingCtx.getString(ai.devcodex.agent.R.string.chat_thinking_dur_sec_fmt, sec))
                        }
                        if (streaming) append(" ▍")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(brush = flowBrush),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
            }
            if (expanded) {
                val thinkScroll = rememberScrollState()
                // 思考流式自动滚动（用户上滑时不抢）
                androidx.compose.runtime.LaunchedEffect(streaming, text.length) {
                    if (streaming && thinkScroll.isScrollInProgress.not()) {
                        runCatching { thinkScroll.animateScrollTo(thinkScroll.maxValue) }
                    }
                }
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp).fillMaxHeight()
                            .background(flowBrush, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    // 思考内容 M3L 富渲染（代码/LaTeX/表格）
                    Column(Modifier.weight(1f).heightIn(max = 360.dp).verticalScroll(thinkScroll)) {
                        ai.devcodex.agent.m3l.MixedMarkdownLatex(
                            text = text,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            isStreaming = streaming,
                        )
                    }
                }
            }
        }
    }
}


/** 消息统计行：模型 · 首包 · 速度 · 上下文 · 缓存命中 */
@Composable
private fun MessageStats(msg: Message, session: ChatSession? = null) {
    if (msg.totalMs <= 0) return
    val speed = if (msg.totalMs > 0 && msg.outputTokens > 0) msg.outputTokens * 1000 / msg.totalMs else 0
    val ctx = msg.inputTokens + msg.outputTokens
    val cachePct = if (msg.inputTokens > 0) msg.cachedTokens * 100 / msg.inputTokens else 0
    // 图标化统计（无 emoji）：上行箭头=输入（括号缓存命中） 下行箭头=输出 闪电=速度 时钟=耗时
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        if (msg.inputTokens > 0) {
            StatIcon(Icons.Filled.ArrowUpward, formatTok(msg.inputTokens) + if (msg.cachedTokens > 0) " (${formatTok(msg.cachedTokens)} cached)" else "")
        }
        if (msg.outputTokens > 0) {
            StatIcon(Icons.Filled.ArrowDownward, formatTok(msg.outputTokens))
        }
        if (speed > 0) StatIcon(Icons.Filled.Bolt, "$speed tok/s")
        if (msg.totalMs > 0) StatIcon(Icons.Filled.Schedule, "%.1fs".format(msg.totalMs / 1000.0))
        // 上下文窗口圆圈：输入/模型上下文上限（默认 128k）
        if (ctx > 0) {
            val limit = (session?.contextOverride?.takeIf { it > 0 }
                ?: ai.devcodex.agent.provider.ModelRegistry.lookup(msg.model ?: "").contextLength
                ?: 128_000)
            val pct = (ctx.toFloat() / limit).coerceIn(0f, 1f)
            val ringTrack = MaterialTheme.colorScheme.outlineVariant
            val ringFill = MaterialTheme.colorScheme.tertiary
            androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
                drawArc(
                    color = ringTrack,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f),
                )
                drawArc(
                    color = ringFill,
                    startAngle = -90f, sweepAngle = 360f * pct, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f),
                )
            }
            Text(formatTok(ctx) + "/" + (limit / 1000) + "k", style = MaterialTheme.typography.labelSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
    }
}

private fun formatTok(n: Long): String = when {
    n >= 1000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}


@Composable
private fun StatIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(text, style = MaterialTheme.typography.labelSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
    }
}


@Composable
private fun MsgActionBar(
    msg: ai.devcodex.agent.data.Message,
    onCopy: (String) -> Unit,
    onResend: () -> Unit,
    onRewind: () -> Unit,
    onDelete: () -> Unit,
) {
    // 气泡下操作栏：图标+微字标签（视觉更轻、语义更明），按下涟漪圆角
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(top = 2.dp, end = 2.dp)) {
        val onSurfVar = MaterialTheme.colorScheme.onSurfaceVariant
        @Composable fun actBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Icon(icon, label, Modifier.size(14.dp), tint = onSurfVar.copy(alpha = 0.75f))
                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = onSurfVar.copy(alpha = 0.7f))
            }
        }
        actBtn(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_copy), Icons.Filled.ContentCopy, onClick = { onCopy(msg.content) })
        if (msg.role == "user") {
            actBtn(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_action_resend), Icons.Filled.Replay, onClick = onResend)
            actBtn(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_action_rewind), Icons.AutoMirrored.Filled.Undo, onClick = onRewind)
        }
        actBtn(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_delete), Icons.Filled.Delete, onClick = onDelete)
    }
}

private fun String.takeOrNull(n: Int): String? = if (isEmpty()) null else take(n)

@Composable
private fun CharAvatarFallback(c: ai.devcodex.agent.data.CharacterCard) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(40.dp).background(
            MaterialTheme.colorScheme.primaryContainer,
            androidx.compose.foundation.shape.CircleShape,
        ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(c.name.takeOrNull(1) ?: androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_char_avatar_fallback), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
