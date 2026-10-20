package ai.devcodex.agent.ui.screens

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.AppSettings
import ai.devcodex.agent.data.ThemePrefsData
import ai.devcodex.agent.shell.ShellAccess
import ai.devcodex.agent.theme.PresetThemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState(initial = ai.devcodex.agent.data.AppSettings())
    var page by remember { mutableStateOf("root") }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(when (page) {
                "theme" -> "主题与字体"
                "tools" -> "工具与权限"
                "stats" -> "使用统计"
                "skills" -> "技能"
                "about" -> "关于"
                else -> "设置"
            })
        }, navigationIcon = {
            if (page != "root") IconButton(onClick = { page = "root" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        })
    }) { padding ->
        when (page) {
            "theme" -> ThemePage(vm, settings, Modifier.padding(padding))
            "tools" -> ToolsPermPage(vm, settings, Modifier.padding(padding))
            "stats" -> ai.devcodex.agent.ui.screens.StatsScreen(vm) { page = "root" }
            "skills" -> SkillsPage(vm) { page = "root" }
            "about" -> AboutPage { page = "root" }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            ) {
                ListItem(
                    headlineContent = { Text("技能 Skills") },
                    supportingContent = { Text("SKILL.md 技能包 · 安装/开关/删除") },
                    leadingContent = { Icon(Icons.Filled.School, null) },
                    modifier = Modifier.clickable { page = "skills" },
                )
                ListItem(
                    headlineContent = { Text("使用统计") },
                    supportingContent = { Text("消息热力图 · 今日/累计数据") },
                    leadingContent = { Icon(Icons.Filled.BarChart, null) },
                    modifier = Modifier.clickable { page = "stats" },
                )
                ListItem(
                    headlineContent = { Text("主题与字体") },
                    supportingContent = { Text("预设主题 · 深色模式 · AMOLED · 字体") },
                    leadingContent = { Icon(Icons.Filled.Palette, null) },
                    modifier = Modifier.clickable { page = "theme" },
                )
                ListItem(
                    headlineContent = { Text("工具与权限") },
                    supportingContent = { Text("内置工具开关 · Shizuku · Root · 无障碍") },
                    leadingContent = { Icon(Icons.Filled.Construction, null) },
                    modifier = Modifier.clickable { page = "tools" },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("关于与赞助") },
                    supportingContent = { Text("作者 · 反馈 · 喝杯奶茶 · 隐私政策") },
                    leadingContent = { Icon(Icons.Filled.Favorite, null) },
                    modifier = Modifier.clickable { page = "about" },
                )
            }
        }
    }
}

@Composable
private fun ThemePage(vm: AppViewModel, settings: AppSettings, modifier: Modifier) {
    val t = settings.theme
    fun save(transform: (ThemePrefsData) -> ThemePrefsData) =
        vm.saveSettings { s -> s.copy(theme = transform(s.theme)) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // ===== 界面偏好 =====
        Text("界面偏好", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(6.dp))
        Text("导航位置", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            listOf("bottom" to "底部", "rail" to "左侧", "immersive" to "沉浸").forEach { (v, label) ->
                FilterChip(selected = t.navPosition == v, onClick = { save { it.copy(navPosition = v) } }, label = { Text(label) })
            }
        }
        if (t.navPosition == "immersive") {
            Text(
                "沉浸模式：仅对话页全屏无导航，通过会话抽屉右上角齿轮回到设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ListItem(
            headlineContent = { Text("沉浸模式（对话页隐藏导航）") },
            supportingContent = { Text("底部/左侧导航时也可单独开启") },
            trailingContent = {
                Switch(checked = t.immersive, onCheckedChange = { on -> save { it.copy(immersive = on) } })
            },
        )
        Spacer(Modifier.padding(12.dp))
        HorizontalDivider()
        Spacer(Modifier.padding(12.dp))

        Text("主题预设", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(PresetThemes) { preset ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { save { it.copy(presetId = preset.id) } },
                ) {
                    Box(
                        Modifier.size(56.dp)
                            .clip(CircleShape)
                            .background(preset.light.primary)
                            .border(
                                width = if (t.presetId == preset.id) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(preset.light.onPrimary))
                    }
                    Spacer(Modifier.padding(4.dp))
                    Text(preset.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.padding(12.dp))

        Text("外观模式", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (v, label) ->
                FilterChip(selected = t.colorMode == v, onClick = { save { it.copy(colorMode = v) } }, label = { Text(label) })
            }
        }
        Spacer(Modifier.padding(8.dp))

        Text("字体", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(6.dp))
        Text("应用字体", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            listOf("gsf" to "Google Sans Flex", "system" to "系统默认").forEach { (v, label) ->
                FilterChip(selected = t.appFont == v, onClick = { save { it.copy(appFont = v) } }, label = { Text(label) })
            }
        }
        Text("对话字体", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            listOf("gsf" to "Google Sans Flex", "mono" to "JetBrains Mono", "system" to "系统默认").forEach { (v, label) ->
                FilterChip(selected = t.chatFont == v, onClick = { save { it.copy(chatFont = v) } }, label = { Text(label) })
            }
        }
        Spacer(Modifier.padding(12.dp))

        ListItem(
            headlineContent = { Text("AMOLED 纯黑") },
            supportingContent = { Text("深色模式下使用纯黑背景（省电）") },
            trailingContent = {
                Switch(checked = t.amoled, onCheckedChange = { on -> save { it.copy(amoled = on) } })
            },
        )
        ListItem(
            headlineContent = { Text("动态取色") },
            supportingContent = { Text("Android 12+ 跟随壁纸取色（覆盖预设）") },
            trailingContent = {
                Switch(checked = t.dynamicColor, onCheckedChange = { on -> save { it.copy(dynamicColor = on) } })
            },
        )
    }
}

@Composable
private fun ToolsPermPage(vm: AppViewModel, settings: AppSettings, modifier: Modifier) {
    val context = LocalContext.current
    var rootChecked by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

        // ============ 权限通道 ============
        Text("权限通道", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(6.dp))

        // Shizuku
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AdminPanelSettings, null)
                    Spacer(Modifier.padding(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Shizuku（ADB 权限）", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (ShellAccess.shizukuAvailable()) "服务运行中" else "未检测到 Shizuku 服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ShellAccess.shizukuAvailable()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.padding(4.dp))
                Text(
                    "通过 Shizuku 获得 ADB 级权限执行 shell 与文件操作。需要先安装 Shizuku App 并按其指引启动服务。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (!ShellAccess.shizukuAvailable()) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app"))) }
                        } else if (!ShellAccess.shizukuPermissionGranted()) {
                            ShellAccess.requestShizukuPermission()
                        } else ShellAccess.bindShizukuService()
                    }) {
                        Text(
                            when {
                                !ShellAccess.shizukuAvailable() -> "获取 Shizuku"
                                !ShellAccess.shizukuPermissionGranted() -> "授权"
                                else -> "重新绑定服务"
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.padding(8.dp))

        // Root
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AdminPanelSettings, null)
                    Spacer(Modifier.padding(6.dp))
                    Column {
                        Text("Root", style = MaterialTheme.typography.titleSmall)
                        Text(
                            rootChecked?.let { if (it) "已授权" else "不可用" } ?: "未检测",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rootChecked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.padding(4.dp))
                Text("通过 su 执行特权命令（无 Shizuku 时的备选通道）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { rootChecked = ShellAccess.rootAvailable() }) { Text("检测 Root") }
            }
        }
        Spacer(Modifier.padding(8.dp))

        // Accessibility
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Accessibility, null)
                    Spacer(Modifier.padding(6.dp))
                    Column {
                        Text("无障碍服务（界面自动化）", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (ai.devcodex.agent.service.DcAccessibilityService.isEnabled()) "已启用" else "未启用",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ai.devcodex.agent.service.DcAccessibilityService.isEnabled()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.padding(4.dp))
                Text("启用后智能体可读取屏幕内容、点击、滑动（ui_snapshot / ui_tap 等工具）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }) { Text("去系统设置开启") }
            }
        }
        Spacer(Modifier.padding(16.dp))

        // ============ 思考推理等级 ============
        Text("思考推理等级", style = MaterialTheme.typography.titleMedium)
        Text("控制模型的思考深度（支持 OpenAI o/gpt-5 系与 Claude 3.7+）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf("" to "默认", "low" to "低", "medium" to "中", "high" to "高").forEach { (v, label) ->
                FilterChip(selected = settings.reasoningEffort == v, onClick = {
                    vm.saveSettings { it.copy(reasoningEffort = v) }
                }, label = { Text(label) })
            }
        }
        Spacer(Modifier.padding(8.dp))

        // ============ MCP 服务器 ============
        Text("MCP 服务器", style = MaterialTheme.typography.titleMedium)
        Text("连接 Streamable HTTP 协议的 MCP 服务，其工具自动注入本应用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(6.dp))
        settings.mcpServers.forEach { m ->
            ListItem(
                overlineContent = { Text("MCP", style = MaterialTheme.typography.labelSmall) },
                headlineContent = { Text(m.name) },
                supportingContent = { Text(m.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Row {
                        Switch(checked = m.enabled, onCheckedChange = { on ->
                            vm.saveSettings { s -> s.copy(mcpServers = s.mcpServers.map { if (it.id == m.id) it.copy(enabled = on) else it }) }
                        })
                        IconButton(onClick = {
                            vm.saveSettings { s -> s.copy(mcpServers = s.mcpServers.filter { it.id != m.id }) }
                        }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) }
                    }
                },
            )
        }
        var newMcpName by remember { mutableStateOf("") }
        var newMcpUrl by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = newMcpName, onValueChange = { newMcpName = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.weight(0.35f), textStyle = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.padding(4.dp))
            OutlinedTextField(value = newMcpUrl, onValueChange = { newMcpUrl = it }, label = { Text("MCP URL (https://…)") }, singleLine = true, modifier = Modifier.weight(0.65f), textStyle = MaterialTheme.typography.bodySmall)
            IconButton(onClick = {
                if (newMcpUrl.isNotBlank()) {
                    val id = java.util.UUID.randomUUID().toString().take(8)
                    vm.saveSettings { s -> s.copy(mcpServers = s.mcpServers + ai.devcodex.agent.provider.McpClient.McpServerConfig(id, newMcpName.ifBlank { "MCP-" + id }, newMcpUrl.trim())) }
                    newMcpName = ""; newMcpUrl = ""
                }
            }) { Icon(Icons.Filled.Add, "添加") }
        }
        Spacer(Modifier.padding(10.dp))

        // ============ 自定义工具 ============
        Text("自定义工具", style = MaterialTheme.typography.titleMedium)
        Text("shell 命令模板，{{参数名}} 会被 AI 填的参数替换，走 Shizuku/Root 执行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(6.dp))
        settings.customTools.forEach { ct ->
            ListItem(
                overlineContent = { Text("自定义", style = MaterialTheme.typography.labelSmall) },
                headlineContent = { Text(ct.name, fontFamily = FontFamily.Monospace) },
                supportingContent = { Text(ct.command, maxLines = 2, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Row {
                        Switch(checked = ct.enabled, onCheckedChange = { on ->
                            vm.saveSettings { s -> s.copy(customTools = s.customTools.map { if (it.name == ct.name) it.copy(enabled = on) else it }) }
                        })
                        IconButton(onClick = {
                            vm.saveSettings { s -> s.copy(customTools = s.customTools.filter { it.name != ct.name }) }
                        }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) }
                    }
                },
            )
        }
        var ctName by remember { mutableStateOf("") }
        var ctDesc by remember { mutableStateOf("") }
        var ctCmd by remember { mutableStateOf("") }
        OutlinedTextField(value = ctName, onValueChange = { ctName = it }, label = { Text("工具名（英文）") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        OutlinedTextField(value = ctDesc, onValueChange = { ctDesc = it }, label = { Text("描述（AI 靠它判断何时用）") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        OutlinedTextField(value = ctCmd, onValueChange = { ctCmd = it }, label = { Text("命令模板，如 pm list packages | grep {{keyword}}") }, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        TextButton(onClick = {
            if (ctName.isNotBlank() && ctCmd.isNotBlank()) {
                vm.saveSettings { s -> s.copy(customTools = s.customTools + ai.devcodex.agent.data.CustomToolDef(ctName.trim(), ctDesc.trim(), "{}", ctCmd.trim())) }
                ctName = ""; ctDesc = ""; ctCmd = ""
            }
        }) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.padding(4.dp)); Text("添加自定义工具") }
        Spacer(Modifier.padding(12.dp))

        // ============ 内置工具开关 ============
        Text("内置工具", style = MaterialTheme.typography.titleMedium)
        Text("关闭后智能体将看不到对应能力", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(6.dp))
        (ai.devcodex.agent.tools.BuiltinTools.all + ai.devcodex.agent.tools.AdvancedFsTools.all).forEach { tool ->
            val enabled = settings.enabledTools.contains(tool.name)
            val alwaysAllowed = settings.toolPerms[tool.name] == "ALLOW"
            ListItem(
                headlineContent = { Text(tool.name, style = MaterialTheme.typography.labelLarge) },
                supportingContent = { Text(tool.description, maxLines = 2, style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { on ->
                                vm.saveSettings { s ->
                                    s.copy(enabledTools = if (on) s.enabledTools + tool.name else s.enabledTools - tool.name)
                                }
                            },
                        )
                    }
                },
            )
            if (enabled && (tool.risk == ai.devcodex.agent.agent.ToolDef.Risk.HIGH || tool.risk == ai.devcodex.agent.agent.ToolDef.Risk.MEDIUM)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                ) {
                    Text("审批策略：", style = MaterialTheme.typography.labelSmall)
                    FilterChip(
                        selected = !alwaysAllowed,
                        onClick = { vm.saveSettings { s -> s.copy(toolPerms = s.toolPerms - tool.name) } },
                        label = { Text("每次询问", style = MaterialTheme.typography.labelSmall) },
                    )
                    Spacer(Modifier.padding(3.dp))
                    FilterChip(
                        selected = alwaysAllowed,
                        onClick = { vm.saveSettings { s -> s.copy(toolPerms = s.toolPerms + (tool.name to "ALLOW")) } },
                        label = { Text("总是允许", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
        Spacer(Modifier.padding(32.dp))
    }
}

/** 技能管理页：列表/开关/删除/从文件安装 */
@Composable
private fun SkillsPage(vm: AppViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var skills by remember { mutableStateOf(listOf<ai.devcodex.agent.data.SkillRepo.Skill>()) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun reload() {
        skills = ai.devcodex.agent.data.SkillRepo(context).list()
    }
    LaunchedEffect(Unit) { reload() }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()?.let { text ->
                ai.devcodex.agent.data.SkillRepo(context).install(text)
                    .onSuccess { reload(); msg = "已安装技能：${it.name}" }
                    .onFailure { msg = "安装失败：${it.message}" }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { picker.launch("*/*") },
                icon = { Icon(Icons.Filled.School, null) },
                text = { Text("安装 SKILL.md") },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // 说明卡
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("技能 = AI 的说明书", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "SKILL.md 格式：简介常驻提示词，正文由 AI 按需读取（skill_read）。\n兼容 agentskills.io 标准，Claude Code / android-skills 生态的技能直接装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            msg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (skills.isEmpty()) {
                // 空态：居中图形 + 引导
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.School, null,
                        Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.padding(10.dp))
                    Text("还没有技能", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "点右下角安装 SKILL.md，或去 GitHub 搜\nagentskills / android-skills 下载技能文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Spacer(Modifier.padding(8.dp))
                Text("已安装 ${skills.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                skills.forEach { sk ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (sk.enabled) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sk.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    if (sk.enabled) "已启用" else "已停用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sk.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable {
                                        ai.devcodex.agent.data.SkillRepo(context).toggle(sk.name); reload()
                                    },
                                )
                                Switch(checked = sk.enabled, onCheckedChange = {
                                    ai.devcodex.agent.data.SkillRepo(context).toggle(sk.name); reload()
                                })
                                IconButton(onClick = {
                                    ai.devcodex.agent.data.SkillRepo(context).delete(sk.name); reload()
                                }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) }
                            }
                            Text(
                                sk.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 关于页：作者/反馈/赞助/隐私政策 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showReward by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showPluginGuide by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // 作者卡
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DevCodex", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "v${ai.devcodex.agent.BuildConfig.VERSION_NAME} · Android AI Agent",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.padding(8.dp))
                Text("作者：刘雨欣", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.padding(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("mqq://im/chat?chat_type=wpa&uin=3633190269"))) }
                    }) { Text("QQ 联系") }
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:Lv520e3e4@163.com"))) }
                    }) { Text("邮件反馈") }
                }
            }
        }
        Spacer(Modifier.padding(10.dp))

        // 反馈信息
        ListItem(
            headlineContent = { Text("反馈意见") },
            supportingContent = { Text("QQ：3633190269\n邮箱：Lv520e3e4@163.com", fontFamily = FontFamily.Monospace) },
        )

        // 赞助
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            onClick = { showReward = true },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🧋", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.padding(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("请作者喝杯奶茶", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("如果 DevCodex 帮到了你，欢迎请作者喝杯奶茶支持一下", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                }
                Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }

        // 检查更新 + 调试日志 + 插件文档（关键入口，多路可达）
        var updateMsg by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        var exporting by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        ListItem(
            headlineContent = { Text("检查更新") },
            supportingContent = { Text(updateMsg ?: "当前 v${ai.devcodex.agent.BuildConfig.VERSION_NAME} · GitHub Releases") },
            leadingContent = { Icon(Icons.Filled.SystemUpdate, null) },
            modifier = Modifier.clickable {
                if (!checking) {
                    checking = true
                    scope.launch {
                        val rel = ai.devcodex.agent.util.AppUpdate.checkLatest()
                        updateMsg = when {
                            rel == null -> "检查失败（网络或仓库不可达）"
                            rel.tagName != "v${ai.devcodex.agent.BuildConfig.VERSION_NAME}" -> "新版本 ${rel.tagName} 可用：Releases 页下载 APK"
                            else -> "已是最新版本"
                        }
                        checking = false
                    }
                }
            },
        )
        ListItem(
            headlineContent = { Text("导出调试日志") },
            supportingContent = { Text(if (exporting) "打包中…" else "崩溃+会话+logcat 打包为 ZIP（在 Android/data 可取出）") },
            leadingContent = { Icon(Icons.Filled.BugReport, null) },
            modifier = Modifier.clickable {
                exporting = true
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val f = ai.devcodex.agent.util.AppUpdate.exportDebugZip(context)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        updateMsg = "调试包已导出：${f.absolutePath}"
                        exporting = false
                    }
                }
            },
        )
        ListItem(
            headlineContent = { Text("插件开发文档") },
            supportingContent = { Text("是什么 · JSON 字段 · 模板语法 · 示例 · 调试") },
            leadingContent = { Icon(Icons.Filled.Extension, null) },
            modifier = Modifier.clickable { showPluginGuide = true },
        )
        // 隐私
        ListItem(
            headlineContent = { Text("隐私政策") },
            supportingContent = { Text("应用隐私与用户数据处理说明") },
            leadingContent = { Icon(Icons.Filled.PrivacyTip, null) },
            modifier = Modifier.clickable { showPrivacy = true },
        )
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(
            "致谢：主题与字体方案源自 rikkahub（Apache-2.0）；Agent 交互模式参考 Operit（Apache-2.0）；设计参考 AiCode。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // 赞赏码弹窗
    if (showReward) {
        AlertDialog(
            onDismissRequest = { showReward = false },
            title = { Text("请作者喝杯奶茶", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(ai.devcodex.agent.R.drawable.reward_qrcode),
                        contentDescription = "微信赞赏码",
                        modifier = Modifier.size(260.dp),
                    )
                    Text("微信扫码 · 金额随意", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showReward = false }) { Text("关闭") } },
        )
    }

    // 插件开发文档（全屏，与插件页同一份）
    if (showPluginGuide) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showPluginGuide = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("插件开发文档") },
                        navigationIcon = { IconButton(onClick = { showPluginGuide = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭") } },
                    )
                },
            ) { pad ->
                Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(PLUGIN_GUIDE_TEXT, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // 隐私政策弹窗
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私政策") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        """DevCodex 隐私政策（应用隐私与用户隐私）

一、数据本地化
DevCodex 不内置任何模型服务，不运营任何服务器。你的会话记录、API 配置（密钥）、工作区文件、技能与插件全部保存在手机本地（应用私有目录），卸载应用即随之删除。

二、网络请求
应用仅在你主动使用时向「你自己配置的 API 提供商」发送对话请求（含你输入的内容与必要的会话上下文）；插件工具会向你安装的插件所声明的 URL 发起请求。我们不代理、不收集、不分析这些流量。

三、敏感权限
· Shizuku/Root：仅在你明确授权后用于执行你批准的命令与文件操作，全部操作有审批记录；
· 无障碍服务：仅在你手动开启后用于屏幕内容读取与界面自动化，服务在你的设备上本地运行；
· 文件访问：工作区为应用私有目录；访问其他路径（如 /sdcard）需要 Shizuku/Root 授权且高危操作逐次确认。

四、崩溃报告
崩溃堆栈仅保存在本地，只有你主动点击"复制"并自行发送，才会离开你的设备。

五、第三方代码致谢
主题方案源自 rikkahub（Apache-2.0）、交互模式参考 Operit（Apache-2.0），其许可与来源见项目仓库。

如对隐私有任何疑问：QQ 3633190269 · Lv520e3e4@163.com（刘雨欣）""",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("我已阅读") } },
        )
    }
}
