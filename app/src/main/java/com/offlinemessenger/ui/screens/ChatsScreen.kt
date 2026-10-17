package com.offlinemessenger.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.offlinemessenger.viewmodel.ChatRow
import com.offlinemessenger.viewmodel.ChatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(onOpenChat: (String) -> Unit) {
    val locator = rememberLocator()
    val vm: ChatsViewModel = viewModel(factory = VmFactory { ChatsViewModel(locator) })
    val chats by vm.chats.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = remember(chats, query) {
        if (query.isBlank()) chats
        else chats.filter {
            it.name.contains(query, ignoreCase = true) || it.lastBody.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("OfflineMessenger") }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
            )
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = "No chats yet",
                    body = "Go to Nearby, wait for an app peer to appear, and start " +
                        "chatting. Messages work with zero internet.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.conversationId }) { row ->
                        ChatListItem(row = row, vm = vm, onClick = { onOpenChat(row.conversationId) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(row: ChatRow, vm: ChatsViewModel, onClick: () -> Unit) {
    val unread by vm.unreadCount(row.conversationId).collectAsState(initial = 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = row.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.verified) {
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
                color = InkMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(row.timestampMs), style = MaterialTheme.typography.bodySmall, color = InkLow)
            Spacer(Modifier.height(4.dp))
            if (unread > 0) {
                Badge { Text("$unread") }
            } else if (row.isMine) {
                Text(
                    when (row.status) {
                        MessageStatus.DELIVERED -> "✓✓"
                        MessageStatus.SENT -> "✓"
                        MessageStatus.PENDING -> "⏳"
                        MessageStatus.FAILED -> "✕"
                        MessageStatus.RECEIVED -> ""
                    },
                    color = if (row.status == MessageStatus.DELIVERED) Emerald400 else InkLow,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
