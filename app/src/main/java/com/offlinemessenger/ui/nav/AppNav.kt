package com.offlinemessenger.ui.nav

import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offlinemessenger.ui.screens.ChatDetailScreen
import com.offlinemessenger.ui.screens.ChatsScreen
import com.offlinemessenger.ui.screens.ContactsScreen
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
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = Tabs.any { it.route == currentRoute }

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
                ChatsScreen(onOpenChat = { id -> nav.navigate("chat/$id") })
            }
            composable(
                route = "chat/{conversationId}",
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
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
}
