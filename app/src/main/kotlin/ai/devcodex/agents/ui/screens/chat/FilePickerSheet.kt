package ai.devcodex.agents.ui.screens.files

import ai.devcodex.agents.data.repo.ProjectFile
import ai.devcodex.agents.ui.AppViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val history = remember { mutableStateOf(mutableListOf<Pair<String?, String>>()) }
    var currentDir by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<ProjectFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var noTree by remember { mutableStateOf(viewModel.projectFilesRepo.getTreeUri() == null) }
    val selected = remember { mutableStateOf<Set<String>>(emptySet()) }

    fun reload() {
        loading = true
        scope.launch {
            entries = viewModel.projectFilesRepo.listChildren(currentDir, currentPath)
            loading = false
        }
    }

    LaunchedEffect(currentDir) { reload() }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.projectFilesRepo.persistTree(uri)
            noTree = false
            currentDir = null
            currentPath = ""
            history.value.clear()
            reload()
        }
    }

    fun navigateTo(dir: ProjectFile) {
        history.value.add(currentDir to currentPath)
        currentDir = dir.uriString
        currentPath = dir.path
        reload()
    }

    fun navigateUp() {
        val prev = history.value.removeLastOrNull() ?: return
        currentDir = prev.first
        currentPath = prev.second
        reload()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (history.value.isNotEmpty()) {
                    IconButton(onClick = { navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("项目文件", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (currentPath.isEmpty()) "根目录" else currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (noTree) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = { treeLauncher.launch(null) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("选择项目目录")
                    }
                }
                Text(
                    text = "选择后 AI 即可读取项目内文件作为上下文",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(entries, key = { it.uriString + it.name }) { file ->
                        FileRow(
                            file = file,
                            selected = file.isDirectory || selected.value.contains(file.path),
                            onSelect = {
                                if (file.isDirectory) {
                                    navigateTo(file)
                                } else {
                                    selected.value = if (selected.value.contains(file.path)) {
                                        selected.value - file.path
                                    } else {
                                        selected.value + file.path
                                    }
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }

                Button(
                    onClick = {
                        val files = entries.filter { selected.value.contains(it.path) }
                        if (files.isNotEmpty()) {
                            viewModel.attachFiles(files)
                            onDismiss()
                        }
                    },
                    enabled = selected.value.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(if (selected.value.isEmpty()) "选择文件（仅目录可展开）" else "添加所选文件（${selected.value.size}）")
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: ProjectFile,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = file.name,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        if (file.isDirectory) {
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(
                imageVector = if (selected) Icons.Filled.AddCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}