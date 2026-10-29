package ai.devcodex.agent.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_IMPORT_BYTES = 512 * 1024
private val PROMPT_KEYS = listOf("system_prompt", "systemPrompt", "system", "prompt", "instructions", "content", "text")

/** 自定义系统提示词：手动编辑 + 从 md/txt/json 等文本文件导入 */
@Composable
fun CustomPromptPage(vm: AppViewModel, settings: AppSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(settings.customSystemPrompt) { mutableStateOf(settings.customSystemPrompt) }
    var appendMode by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { readPromptFile(context, uri) } }
            result.onSuccess { (name, content) ->
                if (content.isBlank()) {
                    info = context.getString(ai.devcodex.agent.R.string.prompt_info_empty_file_fmt, name)
                } else {
                    text = if (appendMode && text.isNotBlank()) text.trimEnd() + "\n\n" + content else content
                    info = context.getString(ai.devcodex.agent.R.string.prompt_info_imported_fmt, name, content.length)
                }
            }.onFailure { info = context.getString(ai.devcodex.agent.R.string.prompt_info_import_failed_fmt, it.message ?: "") }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_title), style = MaterialTheme.typography.titleMedium)
        Text(
            androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            label = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_label)) },
            placeholder = { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_placeholder)) },
        )
        Text(
            androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_char_count_fmt, text.length) +
                if (text.trim() == settings.customSystemPrompt.trim())
                    androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_saved_suffix)
                else androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_unsaved_suffix),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { appendMode = false; picker.launch("*/*") }, modifier = Modifier.weight(1f)) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_import_overwrite)) }
            OutlinedButton(onClick = { appendMode = true; picker.launch("*/*") }, modifier = Modifier.weight(1f)) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_import_append)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val v = text.trim()
                    vm.saveSettings { it.copy(customSystemPrompt = v) }
                    info = context.getString(if (v.isEmpty()) ai.devcodex.agent.R.string.prompt_info_saved_empty else ai.devcodex.agent.R.string.prompt_info_saved)
                },
                modifier = Modifier.weight(1f),
            ) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_save)) }
            OutlinedButton(
                onClick = {
                    text = ""
                    vm.saveSettings { it.copy(customSystemPrompt = "") }
                    info = context.getString(ai.devcodex.agent.R.string.prompt_info_cleared)
                },
                modifier = Modifier.weight(1f),
            ) { Text(androidx.compose.ui.res.stringResource(ai.devcodex.agent.R.string.prompt_clear)) }
        }
        if (info.isNotEmpty()) {
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.padding(8.dp))
    }
}

/** 读取文本文件；.json 按常见字段提取提示词，无法识别时保留原文 */
private fun readPromptFile(context: android.content.Context, uri: Uri): Pair<String, String> {
    val name = displayName(context, uri)
    val bytes = context.contentResolver.openInputStream(uri)?.use { ins ->
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = ins.read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
            if (buf.size() > MAX_IMPORT_BYTES) throw IllegalStateException(context.getString(ai.devcodex.agent.R.string.prompt_err_too_large))
        }
        buf.toByteArray()
    } ?: throw IllegalStateException(context.getString(ai.devcodex.agent.R.string.prompt_err_open_failed))
    if (bytes.any { it == 0.toByte() }) throw IllegalStateException(context.getString(ai.devcodex.agent.R.string.prompt_err_not_text))
    var raw = String(bytes, Charsets.UTF_8)
    if (raw.startsWith("\uFEFF")) raw = raw.substring(1)
    val content = if (name.lowercase().endsWith(".json") || raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[")) {
        extractFromJson(raw)
    } else raw
    return name to content.trim()
}

private fun displayName(context: android.content.Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment ?: context.getString(ai.devcodex.agent.R.string.prompt_default_name)
}

private fun extractFromJson(raw: String): String {
    val el = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return raw
    return findPrompt(el)?.takeIf { it.isNotBlank() } ?: raw
}

private fun findPrompt(el: JsonElement): String? = when (el) {
    is JsonPrimitive -> if (el.isString) el.content else null
    is JsonObject -> {
        PROMPT_KEYS.firstNotNullOfOrNull { k -> (el[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } }
            ?: (el["data"] as? JsonObject)?.let { findPrompt(it) }
    }
    is JsonArray -> {
        // [{"role":"system","content":"..."}] 形式：拼接所有 system 消息
        val parts = el.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            if ((o["role"] as? JsonPrimitive)?.content == "system") (o["content"] as? JsonPrimitive)?.content else null
        }
        if (parts.isEmpty()) null else parts.joinToString("\n\n")
    }
}
