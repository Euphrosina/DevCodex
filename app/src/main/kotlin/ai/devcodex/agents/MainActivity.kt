package ai.devcodex.agents

import ai.devcodex.agents.ui.AppViewModel
import ai.devcodex.agents.ui.screens.chat.ChatScreen
import ai.devcodex.agents.ui.screens.sessions.SessionsScreen
import ai.devcodex.agents.ui.screens.settings.SettingsScreen
import ai.devcodex.agents.ui.theme.DevCodexTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val state by viewModel.uiState.collectAsState()
            DevCodexTheme(
                themeMode = state.settings.themeMode,
                accentColor = state.settings.accentColor,
                font = state.settings.font,
                fontWeight = state.settings.fontWeight,
                fontSize = state.settings.fontSize.sp,
                themePreset = state.settings.themePreset,
                customPrimary = state.settings.customPrimary,
                customBackground = state.settings.customBackground,
                uiStyle = state.settings.uiStyle
            ) {
                MainNavigation(viewModel = viewModel)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("对话", Icons.Filled.Chat),
    HISTORY("会话", Icons.Filled.History),
    SETTINGS("设置", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavigation(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    var currentTab by rememberSaveable { mutableStateOf(Tab.CHAT) }

    val title = when (currentTab) {
        Tab.CHAT -> state.conversations.firstOrNull { it.id == state.selectedId }?.title ?: "对话"
        Tab.HISTORY -> "会话"
        Tab.SETTINGS -> "设置"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, maxLines = 1)
                        if (currentTab == Tab.CHAT) {
                            Text(
                                text = "${state.settings.provider.displayName} · ${state.settings.model}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                Tab.CHAT -> ChatScreen(viewModel = viewModel)
                Tab.HISTORY -> SessionsScreen(viewModel = viewModel, onOpenChat = { currentTab = Tab.CHAT })
                Tab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}