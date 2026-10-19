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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                title = { Text(if (currentDir.isBlank()) "工作区" else currentDir) },
                navigationIcon = {
                    if (currentDir.isNotBlank()) {
                        IconButton(onClick = {
                            currentDir = currentDir.substringBeforeLast('/').ifBlank { "" }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch("*/*") }) {
                Icon(Icons.Filled.UploadFile, "上传文件")
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("工作区是空的", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "点右下角上传文件（文档/代码/图片…）。" +
                        "上传后：\n· 聊天输入栏 📎 可引用文件让 AI 处理\n" +
                        "· AI 工具用相对路径即可读写（fs_read \"文件名\"）\n" +
                        "· 高危路径操作仍需你的授权",
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
                                (if (e.isDir) "目录" else repo.formatSize(e.size)) + " · " + repo.formatDate(e.modified),
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
                            }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) }
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

    // 文件预览
    preview?.let { e ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${repo.formatSize(e.size)} · ${e.relativePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (repo.isTextFile(e)) WorkspaceRepo.textPreview(e.file)
                        else "（二进制文件，AI 可通过路径访问：${e.relativePath}）",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                val clipboard = LocalClipboardManager.current
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString("@${e.name}"))
                    preview = null
                }) { Text("复制引用") }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) { Text("关闭") }
            },
        )
    }
}
