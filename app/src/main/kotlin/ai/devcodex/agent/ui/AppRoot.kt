package ai.devcodex.agent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.devcodex.agent.AppViewModel
import ai.devcodex.agent.data.AppSettings
import ai.devcodex.agent.ui.screens.ChatScreen
import ai.devcodex.agent.ui.screens.PluginsScreen
import ai.devcodex.agent.ui.screens.ProvidersScreen
import ai.devcodex.agent.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

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
    val settings by vm.settings.collectAsState(initial = AppSettings())
    val theme = settings.theme

    fun navigate(i: Int) {
        if (tabIdx != i) {
            tabIdx = i
            nav.navigate(tabs[i].route) {
                popUpTo(nav.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.toast.collect { msg -> msg?.let { snackbar.showSnackbar(it); vm.toast.value = null } }
    }
    // 页面跳转请求（沉浸模式设置出口等）
    LaunchedEffect(Unit) {
        vm.navRequest.collect { req ->
            if (req != null) {
                val i = tabs.indexOfFirst { it.route == req }
                if (i >= 0) navigate(i)
                vm.navRequest.value = null
            }
        }
    }
    LaunchedEffect(Unit) {
        if (vm.activeSession.value == null) {
            vm.sessions.value.firstOrNull()?.let { vm.openSession(it.id) } ?: vm.openSession(null)
        }
    }

    // 导航模式：底部栏 / 左侧栏 / 沉浸隐藏（仅对话页）
    val showNav = !(theme.immersive && tabs[tabIdx].route == "chat")

    Surface(color = MaterialTheme.colorScheme.background) {
        when {
            theme.navPosition == "rail" && showNav -> {
                // 左侧导航栏（横屏/平板友好，可隐藏底栏）
                Row(Modifier.padding()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        tabs.forEachIndexed { i, t ->
                            NavigationRailItem(
                                selected = tabIdx == i,
                                onClick = { navigate(i) },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbar) },
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    ) { padding ->
                        NavHost(
                            navController = nav,
                            startDestination = "chat",
                            modifier = Modifier.padding(padding),
                        ) {
                            composable("chat") { ChatScreen(vm) }
                            composable("workspace") { ai.devcodex.agent.ui.screens.WorkspaceScreen(vm) }
                            composable("providers") { ProvidersScreen(vm) }
                            composable("plugins") { PluginsScreen(vm) }
                            composable("settings") { SettingsScreen(vm) }
                        }
                    }
                }
            }
            theme.navPosition == "immersive" || !showNav -> {
                // 沉浸模式：无导航栏，仅对话（从设置进出）
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = "chat",
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("chat") { ChatScreen(vm) }
                        composable("workspace") { ai.devcodex.agent.ui.screens.WorkspaceScreen(vm) }
                        composable("providers") { ProvidersScreen(vm) }
                        composable("plugins") { PluginsScreen(vm) }
                        composable("settings") { SettingsScreen(vm) }
                    }
                }
            }
            else -> {
                // 默认：底部导航
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showNav) {
                            NavigationBar {
                                tabs.forEachIndexed { i, t ->
                                    NavigationBarItem(
                                        selected = tabIdx == i,
                                        onClick = { navigate(i) },
                                        icon = { Icon(t.icon, contentDescription = t.label) },
                                        label = { Text(t.label) },
                                    )
                                }
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
                        composable("workspace") { ai.devcodex.agent.ui.screens.WorkspaceScreen(vm) }
                        composable("providers") { ProvidersScreen(vm) }
                        composable("plugins") { PluginsScreen(vm) }
                        composable("settings") { SettingsScreen(vm) }
                    }
                }
            }
        }
    }
}
