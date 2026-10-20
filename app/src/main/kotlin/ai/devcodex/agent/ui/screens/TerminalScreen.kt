package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 终端窗口（走 Shell 通道：Shizuku/Root，工作目录=工作区） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: ai.devcodex.agent.AppViewModel) {
    var lines by remember { mutableStateOf(mutableListOf("\u001b[1mDevCodex Shell\u001b[0m（Shizuku/Root 通道）\n输入命令回车执行；exit 清屏")) }
    var cmd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val mono = FontFamily.Monospace

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("终端") },
                navigationIcon = {
                    IconButton(onClick = { vm.navRequest.value = "chat" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // 输出区
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                lines.forEach { l ->
                    Text(l, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
            // 输入行
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$ ", fontFamily = mono, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = cmd, onValueChange = { cmd = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !busy,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 14.sp),
                    placeholder = { Text("命令", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions.Default,
                )
                IconButton(onClick = {
                    if (cmd.isNotBlank() && !busy) {
                        val c = cmd; cmd = ""
                        lines = (lines + "\u001b[36m$ c\u001b[0m").toMutableList()
                        busy = true
                        scope.launch(Dispatchers.IO) {
                            val out = ai.devcodex.agent.shell.ShellAccess.exec(c).take(20000)
                            withContext(Dispatchers.Main) {
                                lines = (lines + out).toMutableList()
                                busy = false
                            }
                        }
                    }
                }) { Icon(Icons.Filled.PlayArrow, "执行") }
            }
        }
    }
}
