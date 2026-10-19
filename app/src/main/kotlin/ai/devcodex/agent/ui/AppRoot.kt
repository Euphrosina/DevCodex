package ai.devcodex.agent.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.R
import ai.devcodex.agent.ui.screens.ChatScreen
import ai.devcodex.agent.ui.screens.PluginsScreen
import ai.devcodex.agent.ui.screens.ProvidersScreen
import ai.devcodex.agent.ui.screens.SettingsScreen
import ai.devcodex.agent.ui.screens.WorkspaceScreen

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppRoot(vm: AppViewModel) {
    val tabs = remember {
        listOf(
            Tab("chat", "对话", Icons.Filled.Chat),
            Tab("workspace", "工作区", Icons.Filled.Folder),
            Tab("providers", "提供商", Icons.Filled.AutoAwesome),
            Tab("plugins", "插件", Icons.Filled.Extension),
            Tab("settings", "设置", Icons.Filled.Settings),
        )
    }
    var tabIdx by remember { mutableIntStateOf(0) }
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.toast.collect { msg -> msg?.let { snackbar.showSnackbar(it); vm.toast.value = null } }
    }
    // 首次进入：创建/恢复会话
    LaunchedEffect(Unit) {
        if (vm.activeSession.value == null) {
            vm.sessions.value.firstOrNull()?.let { vm.openSession(it.id) } ?: vm.openSession(null)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIdx == i,
                        onClick = {
                            if (tabIdx != i) {
                                tabIdx = i
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "chat",
            modifier = Modifier.padding(padding),
        ) {
            composable("chat") { ChatScreen(vm) }
            composable("providers") { ProvidersScreen(vm) }
            composable("workspace") { WorkspaceScreen(vm) }
            composable("plugins") { PluginsScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
