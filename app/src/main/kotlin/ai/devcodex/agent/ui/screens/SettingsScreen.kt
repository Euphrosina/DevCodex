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
import androidx.compose.foundation.layout.width
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Button
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
        // 页面跳转请求（如 /stats → stats 子页）
        LaunchedEffect(Unit) {
            vm.settingsPageRequest.collect { req ->
                if (req != null) {
                    page = req
                    vm.settingsPageRequest.value = null
                }
            }
        }
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
        // ===== 个性化：昵称与头像 =====
        Text("个性化", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("显示昵称") },
            supportingContent = { Text("在头像上方显示 AI 与用户昵称") },
            trailingContent = {
                Switch(checked = t.showNick, onCheckedChange = { on -> save { it.copy(showNick = on) } })
            },
        )
        ListItem(
            headlineContent = { Text("显示头像") },
            supportingContent = { Text("关闭后聊天两侧不显示头像（昵称开关独立）") },
            trailingContent = {
                Switch(checked = t.showAvatar, onCheckedChange = { on -> save { it.copy(showAvatar = on) } })
            },
        )
        var userNick by remember(settings) { mutableStateOf(t.userNick) }
        var aiNick by remember(settings) { mutableStateOf(t.aiNick) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ai.devcodex.agent.ui.components.Avatar(name = t.userNick, avatarFile = t.userAvatar, defaultColor = androidx.compose.ui.graphics.Color(0xFF059669), size = 44.dp)
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(value = userNick, onValueChange = { userNick = it }, label = { Text("你的昵称") }, singleLine = true, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { save { it.copy(userNick = userNick) } }) { Text("保存") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ai.devcodex.agent.ui.components.Avatar(name = t.aiNick, avatarFile = t.aiAvatar, defaultColor = androidx.compose.ui.graphics.Color(0xFF4F46E5), size = 44.dp)
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(value = aiNick, onValueChange = { aiNick = it }, label = { Text("AI 昵称") }, singleLine = true, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { save { it.copy(aiNick = aiNick) } }) { Text("保存") }
        }
        // 头像从图库选择（复制进 filesDir/avatars）
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val userImgPicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val dir = java.io.File(ctx.filesDir, "avatars").apply { mkdirs() }
                val name = "user_" + java.util.UUID.randomUUID().toString().take(6) + ".png"
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.File(dir, name).outputStream().use { input.copyTo(it) }
                }
                save { it.copy(userAvatar = name) }
            }
        }
        val aiImgPicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val dir = java.io.File(ctx.filesDir, "avatars").apply { mkdirs() }
                val name = "ai_" + java.util.UUID.randomUUID().toString().take(6) + ".png"
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.File(dir, name).outputStream().use { input.copyTo(it) }
                }
                save { it.copy(aiAvatar = name) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { userImgPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("更换你的头像") }
            OutlinedButton(onClick = { aiImgPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("更换 AI 头像") }
        }
        Spacer(Modifier.padding(10.dp))

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
                    // MChat 式迷你界面预览卡（真实 preset 配色）
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(preset.light.surface)
                            .border(
                                width = if (t.presetId == preset.id) 2.dp else 0.5.dp,
                                color = if (t.presetId == preset.id) MaterialTheme.colorScheme.primary else preset.light.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(preset.light.primary))
                        Box(
                            Modifier.align(Alignment.End).clip(RoundedCornerShape(6.dp, 6.dp, 2.dp, 6.dp))
                                .background(preset.light.primary).padding(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Hi", style = MaterialTheme.typography.labelSmall, color = preset.light.onPrimary) }
                        Row {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(preset.light.secondary))
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(preset.light.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("AI 回复", style = MaterialTheme.typography.labelSmall, color = preset.light.onSurface)
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF161B22)).padding(4.dp)
                        ) {
                            Text("fun main()", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color(0xFFC9D1D9))
                            Text("  println(ok)", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color(0xFFC3E88D))
                        }
                    }
                    // 旧色圈
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

        // 代码块配色预览（MChat 式）
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("代码块配色预览", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                val pv = ai.devcodex.agent.m3l.darkPaletteForTheme(t.codeTheme)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(pv.background).padding(10.dp)
                ) {
                    Text("fun bubble(msg: Message) {", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = pv.text)
                    Text("    val color = Color(0xFF4285F4)", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = pv.string)
                    Text("    // 主题注释色", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = pv.comment)
                    Text("    return shape(color)", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = pv.keyword)
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    listOf("github" to "GitHub", "dracula" to "Dracula", "onedark" to "One Dark", "solar" to "Solarized", "monokai" to "Monokai", "nord" to "Nord", "gruvbox" to "Gruvbox", "vscode" to "VSCode", "highcontrast" to "高对比").forEach { (v, label) ->
                        FilterChip(selected = t.codeTheme == v, onClick = {
                            save { it.copy(codeTheme = v) }
                            ai.devcodex.agent.m3l.CodeThemeHolder.current = v
                        }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
        Text("外观模式", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("思考内容自动折叠") },
            supportingContent = { Text("开启后，思考结束将自动折叠思考内容") },
            trailingContent = {
                Switch(checked = t.autoCollapseThinking, onCheckedChange = { on -> save { it.copy(autoCollapseThinking = on) } })
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (v, label) ->
                FilterChip(selected = t.colorMode == v, onClick = { save { it.copy(colorMode = v) } }, label = { Text(label) })
            }
        }
        Spacer(Modifier.padding(8.dp))

        Text("字体", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(6.dp))
        // MChat 式：两行各带预览文字的字体选择
        val appFonts = listOf("gsf" to "Sans Flex", "serif" to "衬线", "anthropic" to "Anthropic", "oplus" to "Oplus Serif", "mono" to "JetBrains Mono", "system" to "系统默认")
        val chatFonts = listOf("gsf" to "Sans Flex", "serif" to "衬线", "anthropic" to "Anthropic Serif", "oplus" to "Oplus Serif", "mono" to "JetBrains Mono", "system" to "系统默认")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            appFonts.forEach { (v, label) ->
                FilterChip(
                    selected = t.appFont == v,
                    onClick = { save { it.copy(appFont = v) } },
                    label = {
                        Text(
                            "Aa " + label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ai.devcodex.agent.theme.FlexFonts.byId(v),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(4.dp))
        // 自定义字体导入（rikkahub 式：选 ttf/otf → filesDir/fonts → 芯片列表可选）
        val fontPicker = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                runCatching {
                    val fCtx = ai.devcodex.agent.tools.BuiltinTools.appContext()
                    val tmp = java.io.File(fCtx.cacheDir, "font_import_" + System.currentTimeMillis())
                    fCtx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    val name = ai.devcodex.agent.theme.CustomFonts.import(fCtx, tmp)
                    if (name != null) { save { it.copy(chatFont = "custom:" + name) }; vm.toast.value = "字体已导入: " + name } else vm.toast.value = "导入失败"
                    tmp.delete()
                }
            }
        }
        OutlinedButton(onClick = { fontPicker.launch("*/*") }) {
            Icon(Icons.Filled.Add, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("导入自定义字体（ttf/otf）", style = MaterialTheme.typography.labelSmall)
        }
        val fCtx2 = ai.devcodex.agent.tools.BuiltinTools.appContext()
        val customFontNames = androidx.compose.runtime.remember { ai.devcodex.agent.theme.CustomFonts.list(fCtx2) }
        if (customFontNames.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                customFontNames.forEach { name ->
                    FilterChip(
                        selected = t.chatFont == "custom:" + name,
                        onClick = { save { it.copy(chatFont = "custom:" + name) } },
                        label = { Text("Aa " + name.take(8), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("对话字体（聊天气泡）", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chatFonts.forEach { (v, label) ->
                FilterChip(
                    selected = t.chatFont == v,
                    onClick = { save { it.copy(chatFont = v) } },
                    label = {
                        Text(
                            "Aa " + label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ai.devcodex.agent.theme.FlexFonts.byId(v),
                        )
                    },
                )
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

        // ============ AXS / Termux 服务 ============
        Text("AXS / Termux 服务", style = MaterialTheme.typography.titleMedium)
        Text("在 Termux 里启动 AXS 等 HTTP shell 服务后填地址，本应用获得 ADB 级特权通道（优先于沙盒）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var axsInput by remember { mutableStateOf(settings.axsUrl) }
        OutlinedTextField(
            value = axsInput, onValueChange = { axsInput = it },
            label = { Text("服务地址（如 http://127.0.0.1:8912）") },
            singleLine = true, textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                vm.saveSettings { it.copy(axsUrl = axsInput.trim()) }
                ai.devcodex.agent.shell.ShellAccess.axsUrl = axsInput.trim()
            }) { Text("应用") }
            TextButton(onClick = {
                kotlinx.coroutines.GlobalScope.launch {
                    ai.devcodex.agent.shell.ShellAccess.axsUrl = axsInput.trim()
                    val test = ai.devcodex.agent.shell.ShellAccess.axsExec("id")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        axsInput = axsInput  // 触发重绘
                    }
                }
            }) { Text("测试(id)") }
        }
        Spacer(Modifier.padding(10.dp))

        // ============ SSH 隧道 ============
        Text("SSH 隧道", style = MaterialTheme.typography.titleMedium)
        Text("连接远程服务器：AI 可 ssh_exec 远程执行、ssh_forward 建本地端口转发隧道（映射内网 LLM API 等）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var sshHost by remember { mutableStateOf(settings.sshHost) }
        var sshPort by remember { mutableStateOf(settings.sshPort.toString()) }
        var sshUser by remember { mutableStateOf(settings.sshUser) }
        var sshPass by remember { mutableStateOf(settings.sshPassword) }
        var sshKey by remember { mutableStateOf(settings.sshKeyPath) }
        OutlinedTextField(value = sshHost, onValueChange = { sshHost = it }, label = { Text("主机") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text("端口") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.3f))
            OutlinedTextField(value = sshUser, onValueChange = { sshUser = it }, label = { Text("用户名") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
        }
        OutlinedTextField(value = sshPass, onValueChange = { sshPass = it }, label = { Text("密码（或留空用密钥）") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        OutlinedTextField(value = sshKey, onValueChange = { sshKey = it }, label = { Text("私钥路径（可选，如 /sdcard/id_rsa）") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                vm.saveSettings { it.copy(sshHost = sshHost.trim(), sshPort = sshPort.toIntOrNull() ?: 22, sshUser = sshUser.trim(), sshPassword = sshPass, sshKeyPath = sshKey.trim()) }
                ai.devcodex.agent.shell.SshClient.config = if (sshHost.isNotBlank()) ai.devcodex.agent.shell.SshClient.SshConfig(sshHost.trim(), sshPort.toIntOrNull() ?: 22, sshUser.trim(), sshPass, sshKey.trim()) else null
            }) { Text("保存") }
            TextButton(onClick = {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    ai.devcodex.agent.shell.SshClient.config = ai.devcodex.agent.shell.SshClient.SshConfig(sshHost.trim(), sshPort.toIntOrNull() ?: 22, sshUser.trim(), sshPass, sshKey.trim())
                    val r = ai.devcodex.agent.shell.SshClient.exec("echo ok && uname -n")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { sshHost = sshHost }
                }
            }) { Text("测试连接") }
        }
        Spacer(Modifier.padding(10.dp))

        // ============ 后台保护 ============
        Text("后台保护", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("电池优化豁免") },
            supportingContent = { Text("长对话/后台任务不被系统杀（跳系统设置，需手动允许）") },
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:ai.devcodex.agent")))
                }
            },
        )
        Spacer(Modifier.padding(10.dp))

        // ============ 工具审批模式 ============
        Text("工具审批模式", style = MaterialTheme.typography.titleMedium)
        Text("FULL 全自动 · ASSISTED 高危才审（默认） · REQUEST 每步确认。单工具的「总是允许」记忆优先于模式。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            val sp2 = vm.getPreferences()
            val am = sp2.getString("approval_mode", "ASSISTED") ?: "ASSISTED"
            listOf("FULL" to "全自动", "ASSISTED" to "辅助", "REQUEST" to "每步审").forEach { (v, label) ->
                FilterChip(selected = am == v, onClick = { sp2.edit().putString("approval_mode", v).apply(); ai.devcodex.agent.tools.BuiltinTools.appContext().getSharedPreferences("devcodex_prefs", android.content.Context.MODE_PRIVATE).edit().putString("approval_mode", v).apply() }, label = { Text(label) })
            }
        }

        // ============ 工作区（Operit 式自定义） ============
        Text("工作区", style = MaterialTheme.typography.titleMedium)
        val curWs = settings.workspaceDir.ifBlank {
            "应用私有目录（${context.filesDir.absolutePath}/workspace）"
        }
        Text("当前: $curWs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        var wsInput by remember { mutableStateOf(settings.workspaceDir) }
        OutlinedTextField(
            value = wsInput, onValueChange = { wsInput = it },
            label = { Text("自定义工作区绝对路径（留空=应用私有）") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            listOf("" to "私有目录", "/sdcard/DevCodex" to "DevCodex", "/sdcard/Documents" to "Documents", "/sdcard/Download" to "Download").forEach { (v, label) ->
                FilterChip(selected = wsInput == v, onClick = { wsInput = v }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
            }
        }
        var wsMsg by remember { mutableStateOf<String?>(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val dir = wsInput.trim()
                val f = java.io.File(dir)
                val ok = f.exists() && f.isDirectory && f.canWrite() || (f.mkdirs() && f.canWrite())
                wsMsg = if (dir.isBlank()) "已恢复应用私有工作区"
                        else if (ok) "目录可写，已应用"
                        else "目录不可写（需存储权限或改用已存在目录）"
                if (dir.isBlank() || ok) {
                    vm.saveSettings { it.copy(workspaceDir = dir) }
                    ai.devcodex.agent.data.WorkspaceConfig.dir = dir
                }
            }) { Text("应用") }
            if (wsMsg != null) Text(wsMsg!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.padding(10.dp))

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
        val efforts = listOf("", "low", "medium", "high", "xhigh", "max", "ultra")
        val eLabels = listOf("Off", "Low", "Medium", "High", "xHigh", "Max", "Ultra")
        val eCur = efforts.indexOf(settings.reasoningEffort).coerceAtLeast(0)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Faster", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Slider(
                value = eCur.toFloat(),
                onValueChange = { v ->
                    val idx = (v + 0.5f).toInt().coerceIn(0, 6)
                    if (idx != eCur) vm.saveSettings { it.copy(reasoningEffort = efforts[idx]) }
                },
                valueRange = 0f..6f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("Smarter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "当前：${eLabels[eCur]}（Faster 响应快 · Smarter 思考深）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.padding(8.dp))

        // ============ MCP 服务器 ============
        Text("MCP 服务器", style = MaterialTheme.typography.titleMedium)
        Text("连接 Streamable HTTP 协议的 MCP 服务，其工具自动注入本应用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(6.dp))
        settings.mcpServers.forEach { m ->
            ListItem(
                overlineContent = { Text("MCP", style = MaterialTheme.typography.labelSmall) },
                headlineContent = { Text(m.name) },
                supportingContent = { Text((if (m.protocol == "sse") "[SSE] " else "") + m.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        var newMcpProto by remember { mutableStateOf("auto") }
        var newMcpHeaders by remember { mutableStateOf("") }
        OutlinedTextField(value = newMcpName, onValueChange = { newMcpName = it }, label = { Text("名称") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        OutlinedTextField(value = newMcpUrl, onValueChange = { newMcpUrl = it }, label = { Text("MCP URL（streamable 填完整端点 / SSE 填 /sse 端点）") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Text("协议", style = MaterialTheme.typography.labelSmall)
            listOf("auto" to "自动", "streamable" to "Streamable", "sse" to "SSE").forEach { (v, label) ->
                FilterChip(selected = newMcpProto == v, onClick = { newMcpProto = v }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
            }
        }
        OutlinedTextField(value = newMcpHeaders, onValueChange = { newMcpHeaders = it }, label = { Text("自定义头（可选，每行 Key: Value，如 Authorization: Bearer xxx）") }, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        TextButton(onClick = {
            if (newMcpUrl.isNotBlank()) {
                val id = java.util.UUID.randomUUID().toString().take(8)
                val hdrs = newMcpHeaders.lines().mapNotNull { l ->
                    val i = l.indexOf(':')
                    if (i > 0) l.take(i).trim() to l.substring(i + 1).trim() else null
                }.toMap()
                vm.saveSettings { s -> s.copy(mcpServers = s.mcpServers + ai.devcodex.agent.provider.McpClient.McpServerConfig(id, newMcpName.ifBlank { "MCP-" + id }, newMcpUrl.trim(), protocol = newMcpProto, headers = hdrs)) }
                newMcpName = ""; newMcpUrl = ""; newMcpHeaders = ""
            }
        }) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.padding(4.dp)); Text("添加 MCP 服务器") }
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
        (ai.devcodex.agent.tools.BuiltinTools.all + ai.devcodex.agent.tools.AdvancedFsTools.all + ai.devcodex.agent.tools.ExtraTools.all).forEach { tool ->
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

    val repo = ai.devcodex.agent.data.SkillRepo(context)
    var ghUrl by remember { mutableStateOf("") }
    var ghBusy by remember { mutableStateOf(false) }
    val scope = kotlinx.coroutines.MainScope()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val name = (uri.lastPathSegment ?: "").lowercase()
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("读取失败")
                    if (name.endsWith(".zip") || name.endsWith(".skill")) {
                        val tmp = java.io.File(context.cacheDir, "import_skill.zip")
                        tmp.writeBytes(bytes)
                        repo.installFromZipFile(tmp)
                            .onSuccess { reload(); msg = "已安装技能（含附属文件）：${it.name}" }
                            .onFailure { msg = "安装失败：${it.message}" }
                        tmp.delete()
                    } else {
                        repo.install(String(bytes))
                            .onSuccess { reload(); msg = "已安装技能：${it.name}" }
                            .onFailure { msg = "安装失败：${it.message}" }
                    }
                }.onFailure { msg = "读取失败：${it.message}" }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { picker.launch("*/*") },
                icon = { Icon(Icons.Filled.School, null) },
                text = { Text("导入技能包/文件") },
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
            // GitHub 导入卡
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("从 GitHub 导入", style = MaterialTheme.typography.titleSmall)
                    Text("支持 owner/repo、/tree/分支、子目录路径（取该目录 SKILL.md + 附属文件）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedTextField(
                            value = ghUrl, onValueChange = { ghUrl = it },
                            placeholder = { Text("github.com/user/repo/tree/main/skills/foo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                ghBusy = true
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    repo.installFromGithub(ghUrl)
                                        .onSuccess { reload(); msg = "已从 GitHub 安装：${it.name}" }
                                        .onFailure { msg = "GitHub 导入失败：${it.message}" }
                                    ghBusy = false
                                }
                            },
                            enabled = ghUrl.isNotBlank() && !ghBusy,
                        ) { Text("导入") }
                    }
                    if (ghBusy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
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
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // rikkahub 式 logo：圆角图标块
                androidx.compose.foundation.layout.Box(
                    Modifier.size(64.dp).background(
                        MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) { Text("D", style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White) }
                Spacer(Modifier.height(10.dp))
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

        // ===== rikkahub CardGroup 式：版本/系统信息组（primaryContainer） =====
        @Composable fun groupIcon(emoji: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(38.dp).background(container, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
        }
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    leadingContent = { groupIcon("📦", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) },
                    headlineContent = { Text("版本") },
                    supportingContent = { Text("v0.2.2 / 75（持续迭代中）") },
                )
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ListItem(
                    leadingContent = { groupIcon("📱", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) },
                    headlineContent = { Text("系统") },
                    supportingContent = { Text(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE + " / SDK " + android.os.Build.VERSION.SDK_INT) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ===== 链接组（tertiaryContainer）：官网=本项目仓库 / GitHub / QQ 群 =====
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    leadingContent = { groupIcon("🌐", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    headlineContent = { Text("官网") },
                    supportingContent = { Text("https://github.com/Moirania/DevCodex", maxLines = 1) },
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Moirania/DevCodex"))) }
                    },
                )
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ListItem(
                    leadingContent = { groupIcon("⭐", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    headlineContent = { Text("GitHub 仓库") },
                    supportingContent = { Text("开源 · Star · Issues", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Moirania/DevCodex"))) }
                    },
                )
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ListItem(
                    leadingContent = { groupIcon("💬", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                    headlineContent = { Text("QQ 群 · DeepSeek") },
                    supportingContent = { Text("问题反馈 / 交流 / 新版本通知", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://qun.qq.com/universal-share/share?ac=1&authKey=Y0p5CQQcAr6zABChA7uCRctrZqMAJ9XomRgVVRlE1Zt5W9qha%2FqGOkIjqf%2FkMlDf&busi_data=eyJncm91cENvZGUiOiIyMDgxODE2MDQiLCJ0b2tlbiI6IkI0VGE3a0VaL1Q1a3lWYkJYNm5zMU1SeHZnMUozSGxyV0JiVHN5QkQvNXZNUkZnb0taelB0TXpVMEJyQzVoN24iLCJ1aW4iOiIzNjMzMTkwMjY5In0%3D&data=sLRegEFQPFXCtZOVjO6-UPMgaZVw_71eem7w0ii5tcwH8kihxzyXmFDF9twUHtaSUhc43RKwOvcBU4Xa0LRFXQ&svctype=4&tempid=h5_group_info"))) }
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))

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
                            (Regex("b(\\d+)").find(rel.tagName)?.groupValues?.get(1)?.toIntOrNull() ?: 0) > 28 -> "新版本 ${rel.tagName} 可用：Releases 页下载 APK"
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
