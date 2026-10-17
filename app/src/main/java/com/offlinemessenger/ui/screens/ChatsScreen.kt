package com.offlinemessenger.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.components.EmptyState
import com.offlinemessenger.ui.formatTime
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkLow
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.ui.theme.ReadBlue
import com.offlinemessenger.viewmodel.ChatRow
import com.offlinemessenger.viewmodel.ChatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit,
    onCreateGroup: () -> Unit = {},
) {
    val locator = rememberLocator()
    val vm: ChatsViewModel = viewModel(factory = VmFactory { ChatsViewModel(locator) })
    val chats by vm.chats.collectAsState(initial = emptyList())
    val query by vm.searchQuery.collectAsState()
    var pinTarget by remember { mutableStateOf<ChatRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OfflineMessenger") },
                actions = {
                    IconButton(onClick = onCreateGroup) {
                        Icon(Icons.Filled.GroupAdd, "new group", tint = Emerald400)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.searchQuery.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
            )
            if (chats.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = "No chats yet",
                    body = "Go to Nearby, wait for an app peer to appear, and start " +
                        "chatting. Messages work with zero internet.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(chats, key = { it.conversationId }) { row ->
                        ChatListItem(
                            row = row,
                            vm = vm,
                            onClick = { onOpenChat(row.conversationId) },
                            onLongPress = { pinTarget = row },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pinTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { pinTarget = null },
            title = { Text(row.name) },
            text = { Text(if (row.pinned) "Unpin this chat?" else "Pin this chat to the top?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.togglePin(row.conversationId, !row.pinned)
                        pinTarget = null
                    },
                ) { Text(if (row.pinned) "Unpin" else "Pin") }
            },
            dismissButton = {
                TextButton(onClick = { pinTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    row: ChatRow,
    vm: ChatsViewModel,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val unread by vm.unreadCount(row.conversationId).collectAsState(initial = 0)
    val groupName by if (row.isGroup) {
        vm.groupName(row.conversationId.removePrefix("group:")).collectAsState(initial = null)
    } else {
        remember { mutableStateOf<String?>(null) }
    }
    val displayName = if (row.isGroup) groupName ?: "Group" else row.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = displayName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.pinned) {
                    Icon(Icons.Filled.PushPin, "pinned", tint = InkLow, modifier = Modifier.padding(end = 4.dp))
                }
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                )
                if (row.verified && !row.isGroup) {
                    Icon(
                        Icons.Filled.Verified, "verified",
                        tint = Emerald400,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                (if (row.isMine) "You: " else "") + row.lastBody,
                style = MaterialTheme.typography.bodyMedium,
                color = if (unread > 0) MaterialTheme.colorScheme.onSurface else InkMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(row.timestampMs), style = MaterialTheme.typography.bodySmall, color = InkLow)
            Spacer(Modifier.height(4.dp))
            if (unread > 0) {
                Badge(containerColor = Emerald400) { Text("$unread") }
            } else if (row.isMine) {
                Text(
                    tickFor(row.status),
                    color = tickColor(row.status),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (row.status == MessageStatus.READ) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun tickFor(status: MessageStatus): String = when (status) {
    MessageStatus.PENDING -> "⏳"
    MessageStatus.SENT -> "✓"
    MessageStatus.DELIVERED -> "✓✓"
    MessageStatus.READ -> "✓✓"
    MessageStatus.FAILED -> "✕"
    MessageStatus.RECEIVED -> ""
}

private fun tickColor(status: MessageStatus) = when (status) {
    MessageStatus.DELIVERED -> Emerald400
    MessageStatus.READ -> ReadBlue
    else -> InkLow
}
