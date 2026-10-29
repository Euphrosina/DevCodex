package ai.devcodex.agent.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.WorkspaceRepo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(vm: AppViewModel) {
    val repo = remember { vm.workspaceRepo }
    var currentDir by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(repo.list("")) }
    var preview by remember { mutableStateOf<WorkspaceRepo.Entry?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val rootDirLabel = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_root_dir)
    val createFailedMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_create_failed)
    val copiedRefMsg = androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_copied_ref)
    // 进入目录后重读
    androidx.compose.runtime.LaunchedEffect(currentDir, refresh) {
        entries = repo.list(currentDir)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            repo.import(uri).onSuccess { refresh++ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentDir.isBlank()) androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_title) else currentDir) },
                navigationIcon = {
                    if (currentDir.isNotBlank()) {
                        IconButton(onClick = {
                            // 修根级目录无法返回：substring 不变时回根
                            val parent = currentDir.substringBeforeLast('/')
                            currentDir = if (parent == currentDir) "" else parent
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_back)) }
                    }
                },
                actions = {
                    // 新建文件 / 新建文件夹（速度抽屉：折叠菜单）
                    var showNewMenu by remember { mutableStateOf(false) }
                    var showNewFile by remember { mutableStateOf(false) }
                    var showNewDir by remember { mutableStateOf(false) }
                    IconButton(onClick = { showNewMenu = true }) { Icon(Icons.Filled.Add, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_new)) }
                    DropdownMenu(expanded = showNewMenu, onDismissRequest = { showNewMenu = false }) {
                        DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_new_file)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, Modifier.size(18.dp)) }, onClick = { showNewMenu = false; showNewFile = true })
                        DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_new_dir)) }, leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(18.dp)) }, onClick = { showNewMenu = false; showNewDir = true })
                    }
                    if (showNewFile || showNewDir) {
                        var name by remember(showNewFile, showNewDir) { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showNewFile = false; showNewDir = false },
                            title = { Text(if (showNewFile) androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_new_file) else androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_new_dir)) },
                            text = {
                                OutlinedTextField(
                                    value = name, onValueChange = { name = it },
                                    label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_name)) },
                                    singleLine = true,
                                    supportingText = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_create_in_fmt, currentDir.ifBlank { rootDirLabel })) },
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (name.isNotBlank()) {
                                        val path = (currentDir.ifBlank { "" }) + (if (currentDir.isNotBlank()) "/" else "") + name.trim()
                                        val ok = if (showNewFile) repo.createFile(path) else repo.createDir(path)
                                        if (ok) refresh++ else vm.toast.value = createFailedMsg
                                    }
                                    showNewFile = false; showNewDir = false
                                }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_create)) }
                            },
                            dismissButton = { TextButton(onClick = { showNewFile = false; showNewDir = false }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.chat_cancel)) } },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch("*/*") }) {
                Icon(Icons.Filled.UploadFile, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_upload))
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(entries, key = { it.relativePath }) { e ->
                    ListItem(
                        headlineContent = {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                androidx.compose.ui.res.stringResource(
                                    ai.devcodex.agent.R.string.ws_size_date_fmt,
                                    if (e.isDir) androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_dir) else repo.formatSize(e.size),
                                    repo.formatDate(e.modified),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (e.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                null, Modifier.size(28.dp),
                                tint = if (e.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                repo.delete(e.relativePath); refresh++
                            }) { Icon(Icons.Filled.Delete, androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_delete), Modifier.size(18.dp)) }
                        },
                        modifier = Modifier.clickable {
                            if (e.isDir) currentDir = e.relativePath
                            else preview = e
                        },
                    )
                }
            }
        }
    }

    // 文件预览（大卡片 BottomSheet：文本/代码可滚+图片直读+复制引用/删除）
    preview?.let { e ->
        ModalBottomSheet(onDismissRequest = { preview = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        if (e.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${repo.formatSize(e.size)} · ${repo.formatDate(e.modified)} · ${e.relativePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val isImg = e.file.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                if (isImg) {
                    val bmp = remember(e) { repo.readImage(e) }
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = e.name,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        )
                    } else Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_image_decode_failed), style = MaterialTheme.typography.bodySmall)
                } else if (repo.isTextFile(e)) {
                    val content = WorkspaceRepo.textPreview(e.file)
                    val isDiff = e.file.extension.lowercase() in listOf("diff", "patch") || content.trimStart().startsWith("@@ -") || (content.lines().firstOrNull()?.startsWith("--- ") == true && content.contains("@@ "))
                    val isCode = e.file.extension.lowercase() in listOf("kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "c", "h", "cpp", "hpp", "cc", "m", "swift", "go", "rs", "php", "rb", "sql", "sh", "bash", "zsh", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "css", "scss", "html", "htm", "vue", "dart")
                    if (isDiff) {
                        // GitHub 式 Diff 渲染：+绿 -红 @@ 蓝分割行
                        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            content.lines().take(4000).forEach { line ->
                                val bg = when {
                                    line.startsWith("+") -> androidx.compose.ui.graphics.Color(0xFF1B4721)
                                    line.startsWith("-") -> androidx.compose.ui.graphics.Color(0xFF5D1B1B)
                                    line.startsWith("@@") -> androidx.compose.ui.graphics.Color(0xFF123A5D)
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                                val fg = when {
                                    line.startsWith("+") -> androidx.compose.ui.graphics.Color(0xFF7EE787)
                                    line.startsWith("-") -> androidx.compose.ui.graphics.Color(0xFFFF7B72)
                                    line.startsWith("@@") -> androidx.compose.ui.graphics.Color(0xFF79C0FF)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    line.ifBlank { " " },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f),
                                    color = fg,
                                    modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 0.5.dp),
                                )
                            }
                        }
                    } else if (isCode) {
                        // GitHub Dark 语法高亮（#0D1117 底+GitHub 官方配色）——逐段扫描修全红 bug
                        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).background(androidx.compose.ui.graphics.Color(0xFF0D1117))) {
                            val kwR = Regex("\\b(fun|val|var|class|object|if|else|for|while|return|import|package|public|private|static|void|def|let|const|struct|fn|match|async|await)\\b")
                            val strR = Regex("\"[^\"]*\"|'[^']*'")
                            val cmtR = Regex("//[^\\n]*|/\\*.*?\\*/")
                            content.lines().take(3000).forEach { line ->
                                // 收集全部匹配段（range→颜色），按起点排序逐段渲染
                                data class Span(val start: Int, val end: Int, val color: androidx.compose.ui.graphics.Color, val text: String)
                                val spans = buildList {
                                    kwR.findAll(line).forEach { add(Span(it.range.first, it.range.last, androidx.compose.ui.graphics.Color(0xFFFF7B72), it.value)) }
                                    strR.findAll(line).forEach { add(Span(it.range.first, it.range.last, androidx.compose.ui.graphics.Color(0xFFA5D6FF), it.value)) }
                                    cmtR.findAll(line).forEach { add(Span(it.range.first, it.range.last, androidx.compose.ui.graphics.Color(0xFF8B949E), it.value)) }
                                }.sortedBy { it.start }
                                val annotated = androidx.compose.ui.text.buildAnnotatedString {
                                    var pos = 0
                                    for (sp in spans) {
                                        if (sp.start >= pos && sp.start < line.length) {
                                            if (sp.start > pos) append(line.substring(pos, sp.start))
                                            withStyle(androidx.compose.ui.text.SpanStyle(color = sp.color)) { append(sp.text) }
                                            pos = sp.end + 1
                                        }
                                    }
                                    if (pos < line.length) append(line.substring(pos))
                                }
                                Text(
                                    annotated,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f, color = androidx.compose.ui.graphics.Color(0xFFC9D1D9)),
                                )
                            }
                        }
                    } else if (e.file.extension.lowercase() in listOf("md", "markdown", "txt")) {
                        // Markdown 预览走 M3L（表格/代码/LaTeX 完整渲染）
                        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            ai.devcodex.agent.m3l.MixedMarkdownLatex(
                                text = content,
                                textColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        Text(
                            content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Text(
                        androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_binary_fmt, e.relativePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    val clipboard = LocalClipboardManager.current
                    TextButton(onClick = { clipboard.setText(AnnotatedString("@${e.name}")); vm.toast.value = copiedRefMsg }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.ws_copy_ref)) }
                    TextButton(onClick = { repo.delete(e.relativePath); preview = null; refresh++ }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                    androidx.compose.material3.Button(onClick = { preview = null }) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.common_close)) }
                }
            }
        }
    }
}
