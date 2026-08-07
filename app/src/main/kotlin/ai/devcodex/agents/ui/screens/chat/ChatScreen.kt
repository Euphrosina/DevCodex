package ai.devcodex.agents.ui.screens.chat

import ai.devcodex.agents.data.model.MessageRole
import ai.devcodex.agents.ui.AppViewModel
import ai.devcodex.agents.ui.screens.files.FilePickerSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    val conversation = state.conversations.firstOrNull { it.id == state.selectedId }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showFilePicker by remember { mutableStateOf(false) }

    val lastMessageCount = conversation?.messages?.size ?: 0
    LaunchedEffect(lastMessageCount, state.streaming, conversation?.messages?.lastOrNull()?.content) {
        if (conversation != null && conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (conversation == null || conversation.messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DevCodex", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("AI 编码助手，在下方输入问题开始对话", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(conversation.messages) { message ->
                    MessageBubble(
                        message = message,
                        showModel = message.role == MessageRole.ASSISTANT && message.content.isNotEmpty()
                    )
                }
                if (state.streaming && conversation.messages.lastOrNull()?.content?.isEmpty() == true) {
                    item {
                        ThinkingIndicator()
                    }
                }
            }
        }

        if (state.attachedFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.attachedFiles.take(3).forEach { file ->
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1
                            )
                            IconButton(onClick = { viewModel.removeAttached(file.path) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Text(
                    text = "共 ${state.attachedFiles.size} 个文件",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = { showFilePicker = true }) {
                Icon(Icons.Filled.AttachFile, contentDescription = "添加文件上下文")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 160.dp),
                placeholder = { Text("输入问题，Shift+Enter 换行") },
                maxLines = 5
            )
            if (state.streaming) {
                IconButton(onClick = { viewModel.stopStreaming() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(
                    onClick = {
                        viewModel.sendMessage(input)
                        input = ""
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = if (input.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showFilePicker) {
        FilePickerSheet(
            viewModel = viewModel,
            onDismiss = { showFilePicker = false }
        )
    }
}

@Composable
fun ThinkingIndicator() {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text("正在思考…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}