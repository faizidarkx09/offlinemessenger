package com.offlinemessenger.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offlinemessenger.ui.screens.ChatDetailScreen
import com.offlinemessenger.ui.screens.ChatsScreen
import com.offlinemessenger.ui.screens.ContactsScreen
import com.offlinemessenger.ui.screens.GroupCreateScreen
import com.offlinemessenger.ui.screens.NearbyScreen
import com.offlinemessenger.ui.screens.SettingsScreen
import com.offlinemessenger.ui.screens.TopologyScreen

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Chats : Destination("chats", "Chats", Icons.Filled.ChatBubble)
    data object Nearby : Destination("nearby", "Nearby", Icons.Filled.Radar)
    data object Topology : Destination("topology", "Network", Icons.Filled.Share)
    data object Contacts : Destination("Contacts", "Contacts", Icons.Filled.Contacts)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)
}

private val Tabs = listOf(
    Destination.Chats,
    Destination.Nearby,
    Destination.Topology,
    Destination.Contacts,
    Destination.Settings,
)

@Composable
fun AppNav(
    startChatId: String? = null,
    onChatOpened: () -> Unit = {},
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = Tabs.any { it.route == currentRoute }
    val locator = com.offlinemessenger.ui.rememberLocator()
    val repo = remember(locator) { locator.chatRepository }
    val uiScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }

    // Deep link from a notification tap / request accept.
    LaunchedEffect(startChatId) {
        if (startChatId != null) {
            nav.navigate("chat/$startChatId")
            onChatOpened()
        }
    }

    // Incoming chat request while the app is open: show a dialog.
    var pendingRequest by remember { mutableStateOf<com.offlinemessenger.repository.ConnRequest?>(null) }
    val peers = remember(locator) { locator.peerRepository }
    val contacts by peers.contacts.collectAsState(initial = emptyList())
    LaunchedEffect(repo) {
        launch { repo.incomingRequests.collect { pendingRequest = it } }
        // Our request was accepted: open the chat immediately.
        launch { repo.requestAccepted.collect { peerId -> nav.navigate("chat/$peerId") } }
    }

    Scaffold(
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Destination.Chats.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Chats.route) {
                ChatsScreen(
                    onOpenChat = { id -> nav.navigate("chat/$id") },
                    onCreateGroup = { nav.navigate("group_create") },
                )
            }
            composable(
                route = "group_create",
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { slideOutHorizontally { it } + fadeOut() },
                popEnterTransition = { slideInHorizontally { -it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() },
            ) {
                GroupCreateScreen(
                    onBack = { nav.popBackStack() },
                    onGroupCreated = { id ->
                        nav.popBackStack()
                        nav.navigate("chat/$id")
                    },
                )
            }
            composable(
                route = "chat/{conversationId}",
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { slideOutHorizontally { it } + fadeOut() },
                popEnterTransition = { slideInHorizontally { -it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() },
            ) { entry ->
                ChatDetailScreen(
                    conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Destination.Nearby.route) { NearbyScreen() }
            composable(Destination.Topology.route) { TopologyScreen() }
            composable(Destination.Contacts.route) {
                ContactsScreen(onOpenChat = { id -> nav.navigate("chat/$id") })
            }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }

    // In-app chat-request dialog (foreground case; background is the notification).
    pendingRequest?.let { req ->
        val c = contacts.firstOrNull { it.deviceId == req.peerId }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRequest = null },
            title = { Text("Chat request") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.offlinemessenger.ui.components.Avatar(
                        name = c?.displayName ?: "Nearby peer",
                        size = 48.dp,
                        photoB64 = c?.photoB64 ?: "",
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${c?.displayName ?: "Someone nearby"} wants to chat with you.")
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingRequest = null
                    uiScope.launch {
                        repo.acceptConnectionRequest(
                            java.util.UUID.fromString(req.peerId),
                        )
                        nav.navigate("chat/${req.peerId}")
                    }
                }) { Text("Accept") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingRequest = null
                    uiScope.launch {
                        repo.declineConnectionRequest(
                            java.util.UUID.fromString(req.peerId),
                        )
                    }
                }) { Text("Decline") }
            },
        )
    }
}
