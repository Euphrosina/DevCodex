package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 可视化终端（OpenMinis 式 ANSI 模拟器 + Canvas 渲染；通道：Shizuku/Root/沙盒自动降级） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: ai.devcodex.agent.AppViewModel) {
    val emulator = remember { ai.devcodex.agent.term.emulator.TerminalEmulator(cols = 80, rows = 24) }
    var rev by remember { mutableStateOf(0) }   // 重绘触发
    var cmd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(mutableListOf<String>()) }
    var histIdx by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()

    fun banner() {
        emulator.feed("DevCodex VT 终端（OpenMinis 模拟器内核）\r\n通道: Shizuku / Root / 内置沙盒 自动选择\r\n工作目录: ${ai.devcodex.agent.tools.BuiltinTools.workspaceRoot().absolutePath}\r\n\u001b[90m↑↓ 历史 · 回车执行 · 沙盒输出带 [sandbox] 前缀\u001b[0m\r\n$ ".toByteArray())
        rev++
    }
    LaunchedEffect(Unit) { banner() }

    fun runCommand(c: String) {
        if (busy) return
        busy = true
        emulator.feed("\r\n".toByteArray()); rev++
        scope.launch(Dispatchers.IO) {
            val out = ai.devcodex.agent.shell.ShellAccess.exec(c)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                emulator.feed(out.toByteArray())
                emulator.feed("\r\n$ ".toByteArray())
                rev++
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("终端", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { vm.navRequest.value = "chat" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 清屏
                    TextButton(onClick = {
                        emulator.feed("\u001b[2J\u001b[H".toByteArray())
                        emulator.feed("$ ".toByteArray())
                        rev++
                    }) { Text("清屏", style = MaterialTheme.typography.labelMedium) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // 通道指示
            val mode = ai.devcodex.agent.shell.ShellAccess.availableMode()
            val modeLabel = when (mode) {
                ai.devcodex.agent.shell.ShellAccess.Mode.SHIZUKU -> "Shizuku（特权）"
                ai.devcodex.agent.shell.ShellAccess.Mode.ROOT -> "Root（特权）"
                ai.devcodex.agent.shell.ShellAccess.Mode.SANDBOX -> "内置沙盒（应用权限）"
            }
            Text(
                "通道: $modeLabel",
                style = MaterialTheme.typography.labelSmall,
                color = if (mode == ai.devcodex.agent.shell.ShellAccess.Mode.SANDBOX) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            // ANSI 终端渲染区（颜色/光标/转义全支持）
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(rev) {
                    ai.devcodex.agent.term.canvas.TerminalCanvasView(
                        emulator = emulator,
                        modifier = Modifier.fillMaxSize(),
                        onResize = { _, _ -> },
                    )
                }
            }
            // 输入行（等宽）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$ ", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = cmd, onValueChange = { cmd = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !busy,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    placeholder = { Text(if (busy) "执行中…" else "命令", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                )
                IconButton(onClick = {
                    if (cmd.isNotBlank()) {
                        val c = cmd
                        history.add(c); histIdx = -1
                        cmd = ""
                        runCommand(c)
                    }
                }) { Icon(Icons.Filled.PlayArrow, "执行") }
            }
        }
    }
}
