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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Palette
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
                else -> "设置"
            })
        }, navigationIcon = {
            if (page != "root") TextButton(onClick = { page = "root" }) { Text("← 返回") }
        })
    }) { padding ->
        when (page) {
            "theme" -> ThemePage(vm, settings, Modifier.padding(padding))
            "tools" -> ToolsPermPage(vm, settings, Modifier.padding(padding))
            "stats" -> ai.devcodex.agent.ui.screens.StatsScreen(vm) { page = "root" }
            "skills" -> SkillsPage(vm) { page = "root" }
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
                    headlineContent = { Text("关于") },
                    supportingContent = { Text("DevCodex v3.1.0 · AI Agent for Android\n主题与字体方案源自 rikkahub（Apache-2.0）\nAgent 交互模式参考 Operit（Apache-2.0）") },
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

        // ============ 内置工具开关 ============
        Text("内置工具", style = MaterialTheme.typography.titleMedium)
        Text("关闭后智能体将看不到对应能力", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(6.dp))
        ai.devcodex.agent.tools.BuiltinTools.all.forEach { tool ->
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("技能（Skills）", style = MaterialTheme.typography.titleMedium)
                Text(
                    "SKILL.md 格式技能包：简介常驻提示词，正文由 AI 按需读取。兼容 agentskills.io 标准（Claude Code / android-skills 生态）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { picker.launch("*/*") }) { Text("从文件安装") }
        }
        msg?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (skills.isEmpty()) {
            Text(
                "暂无技能。可从 Claude Code 生态的 SKILL.md 文件安装（GitHub 上搜 agentskills 或 android/skills）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        skills.forEach { sk ->
            ListItem(
                overlineContent = { Text(if (sk.enabled) "已启用" else "已停用", style = MaterialTheme.typography.labelSmall) },
                headlineContent = { Text(sk.name) },
                supportingContent = { Text(sk.description, maxLines = 2, style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Row {
                        Switch(checked = sk.enabled, onCheckedChange = {
                            ai.devcodex.agent.data.SkillRepo(context).toggle(sk.name); reload()
                        })
                        IconButton(onClick = {
                            ai.devcodex.agent.data.SkillRepo(context).delete(sk.name); reload()
                        }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) }
                    }
                },
            )
        }
    }
}
