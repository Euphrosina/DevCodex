package ai.devcodex.agents.ui.screens.settings

import ai.devcodex.agents.data.model.AccentColor
import ai.devcodex.agents.data.model.AppFont
import ai.devcodex.agents.data.model.FontWeightSetting
import ai.devcodex.agents.data.model.ProviderType
import ai.devcodex.agents.data.model.SettingsData
import ai.devcodex.agents.data.model.ThemeMode
import ai.devcodex.agents.data.model.ThemePreset
import ai.devcodex.agents.data.model.UiStyle
import ai.devcodex.agents.ui.AppViewModel
import ai.devcodex.agents.ui.screens.about.AboutScreen
import ai.devcodex.agents.ui.theme.accentPrimaryColor
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private val CustomColors = listOf(
    Color(0xFF6750A4), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFE65100),
    Color(0xFFC2185B), Color(0xFF00695C), Color(0xFFBB86FC), Color(0xFF007ACC),
    Color(0xFF58A6FF), Color(0xFF83A598), Color(0xFFC8A27D), Color(0xFF8B5CF6),
    Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFF06B6D4),
    Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF0EA5E9), Color(0xFFF97316),
    Color(0xFF121212), Color(0xFFFFFFFF)
)

private fun colorToLong(color: Color): Long = color.value.toLong()

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.settings
    var showAbout by rememberSaveable { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("外观", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        ThemePreviewCard()

        Spacer(Modifier.height(12.dp))
        Text("界面风格", style = MaterialTheme.typography.titleSmall)
        UiStyle.entries.forEach { style ->
            OptionRow(
                label = style.displayName,
                selected = settings.uiStyle == style,
                onClick = { viewModel.updateSettings(settings.copy(uiStyle = style)) }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("主题预设", style = MaterialTheme.typography.titleSmall)
        ThemePreset.entries.forEach { preset ->
            OptionRow(
                label = preset.displayName,
                selected = settings.themePreset == preset,
                onClick = { viewModel.updateSettings(settings.copy(themePreset = preset)) }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("主题模式（仅默认预设生效）", style = MaterialTheme.typography.titleSmall)
        ThemeMode.entries.forEach { mode ->
            OptionRow(
                label = mode.displayName,
                selected = settings.themeMode == mode,
                onClick = { viewModel.updateSettings(settings.copy(themeMode = mode)) }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("强调色", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AccentColor.entries.forEach { accent ->
                val color = accentPrimaryColor(accent, darkTheme = false)
                val selected = settings.accentColor == accent
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (selected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { viewModel.updateSettings(settings.copy(accentColor = accent)) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("自定义颜色（覆盖当前主题）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("主色", style = MaterialTheme.typography.bodySmall)
        ColorPaletteRow(
            selected = settings.customPrimary,
            onSelect = { viewModel.updateSettings(settings.copy(customPrimary = it)) }
        )
        Spacer(Modifier.height(8.dp))
        Text("背景色", style = MaterialTheme.typography.bodySmall)
        ColorPaletteRow(
            selected = settings.customBackground,
            onSelect = { viewModel.updateSettings(settings.copy(customBackground = it)) }
        )
        Row {
            TextButton(
                onClick = { viewModel.updateSettings(settings.copy(customPrimary = null, customBackground = null)) },
                enabled = settings.customPrimary != null || settings.customBackground != null
            ) {
                Text("恢复默认颜色")
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("字体", style = MaterialTheme.typography.titleSmall)
        AppFont.entries.forEach { font ->
            OptionRow(
                label = font.displayName,
                selected = settings.font == font,
                onClick = { viewModel.updateSettings(settings.copy(font = font)) }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("字体粗细", style = MaterialTheme.typography.titleSmall)
        FontWeightSetting.entries.forEach { weight ->
            OptionRow(
                label = weight.displayName,
                selected = settings.fontWeight == weight,
                onClick = { viewModel.updateSettings(settings.copy(fontWeight = weight)) }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("字号：${"%.0f".format(settings.fontSize)}sp", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.fontSize,
            onValueChange = { viewModel.updateSettings(settings.copy(fontSize = it)) },
            valueRange = 12f..24f,
            steps = 11
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(16.dp))

        Text("AI 服务配置", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Text("服务商", style = MaterialTheme.typography.titleSmall)
        ProviderType.entries.forEach { provider ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.updateSettings(
                            settings.copy(
                                provider = provider,
                                baseUrl = provider.defaultBaseUrl(),
                                model = provider.defaultModel()
                            )
                        )
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(provider.displayName, style = MaterialTheme.typography.bodyMedium)
                if (settings.provider == provider) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = settings.baseUrl,
            onValueChange = { viewModel.updateSettings(settings.copy(baseUrl = it)) },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (settings.provider != ProviderType.OLLAMA) {
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { viewModel.updateSettings(settings.copy(apiKey = it)) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = settings.model,
            onValueChange = { viewModel.updateSettings(settings.copy(model = it)) },
            label = { Text("模型名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        Text("Temperature：${"%.1f".format(settings.temperature)}", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = settings.temperature,
            onValueChange = { viewModel.updateSettings(settings.copy(temperature = it)) },
            valueRange = 0f..1.5f,
            steps = 14
        )

        Row {
            TextButton(onClick = {
                val p = settings.provider
                viewModel.updateSettings(
                    settings.copy(
                        baseUrl = p.defaultBaseUrl(),
                        model = p.defaultModel(),
                        temperature = 0.7f
                    )
                )
            }) {
                Text("恢复默认")
            }
            TextButton(onClick = { viewModel.testConnection() }, enabled = !state.testing) {
                if (state.testing) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp))
                } else {
                    Text("测试连接")
                }
            }
        }

        state.testResult?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("连接成功", ignoreCase = true)) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(16.dp))

        Text("外观说明", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "• 界面风格：Material3 为标准圆角；MIUI 风格采用更大的圆角（HyperOS 视觉语言），可与任意主题搭配。\n" +
                "• 主题预设内置 7 套配色（深色/浅色/咖啡/VS Code/GitHub深色/Gruvbox/高对比度）。\n" +
                "• 选择预设后可通过自定义主色、背景色进一步调整，实时生效。\n" +
                "• 默认预设下：默认紫且 Android 12+ 使用系统动态取色，其余强调色固定配色。\n" +
                "• 内置字体均为开源字体，离线可用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(16.dp))

        Text("关于", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        OptionRow(label = "关于软件（作者、隐私政策、协议）", selected = false, onClick = { showAbout = true })

        Spacer(Modifier.height(24.dp))
        Text("说明", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "• OpenAI 兼容 API 支持 DeepSeek、OpenAI、通义千问等，Base URL 填到 /v1 即可。\n" +
                "• Anthropic 填官方地址 https://api.anthropic.com 与 x-api-key。\n" +
                "• Ollama 本机默认 http://10.0.2.2:11434（模拟器）；真机请填电脑局域网 IP。\n" +
                "• 在聊天页点击附件按钮可选择项目目录，AI 将读取所选文件作为上下文。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun ThemePreviewCard() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface)
            .padding(12.dp)
    ) {
        Text("实时预览", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewSwatch("主色", cs.primary, cs.onPrimary)
            PreviewSwatch("背景", cs.background, cs.onBackground)
            PreviewSwatch("面板", cs.surface, cs.onSurface)
            PreviewSwatch("次要", cs.secondary, cs.onSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "示例文本：这是当前主题的实时预览效果。",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface
        )
    }
}

@Composable
private fun PreviewSwatch(label: String, bg: Color, fg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, fg.copy(alpha = 0.4f), CircleShape)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColorPaletteRow(selected: Long?, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CustomColors.chunked(11).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { color ->
                    val argb = colorToLong(color)
                    val isSelected = selected == argb
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(argb) }
                    )
                }
            }
        }
    }
}