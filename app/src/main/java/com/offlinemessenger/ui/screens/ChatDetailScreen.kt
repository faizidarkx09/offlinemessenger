package com.offlinemessenger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.components.MessageBubble
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.theme.Charcoal700
import com.offlinemessenger.ui.theme.Charcoal950
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.Emerald500
import com.offlinemessenger.ui.theme.InkHigh
import com.offlinemessenger.ui.theme.InkLow
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.viewmodel.ChatDetailViewModel
import com.offlinemessenger.viewmodel.ChatsViewModel

private val QuickEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(conversationId: String, onBack: () -> Unit) {
    val locator = rememberLocator()
    val vm: ChatDetailViewModel = viewModel(
        key = "chat-$conversationId",
        factory = VmFactory { ChatDetailViewModel(locator, conversationId) },
    )
    val chatsVm: ChatsViewModel = viewModel(
        key = "chats-for-forward",
        factory = VmFactory { ChatsViewModel(locator) },
    )
    val messages by vm.messages.collectAsState(initial = emptyList())
    val contact by vm.contact.collectAsState(initial = null)
    val group by vm.group.collectAsState()
    val members by vm.groupMembers.collectAsState(initial = emptyList())
    val replyingTo by vm.replyingTo.collectAsState()
    val sendError by vm.sendError.collectAsState(initial = null)
    var draft by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<MessageEntity?>(null) }
    var forwardFor by remember { mutableStateOf<MessageEntity?>(null) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val myId = remember(locator) { locator.chatRepository.myDeviceId.toString() }
    val uiScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }

    val memberNames = remember(members) {
        members.associate { it.deviceId to it.displayName }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(sendError) {
        sendError?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    val title = when {
        vm.isGroup -> group?.name ?: "Group"
        else -> contact?.displayName ?: "Unknown peer"
    }
    val subtitle = when {
        vm.isGroup -> "${members.size} members · end-to-end encrypted"
        contact == null -> "key not exchanged yet"
        else -> "end-to-end encrypted"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = if (vm.isGroup) title else contact?.displayName ?: "?", size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                if (contact?.verified == true && !vm.isGroup) {
                                    Icon(
                                        Icons.Filled.Verified, "verified",
                                        tint = Emerald400,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMid)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(messages, key = { it.msgId }) { m ->
                    val senderName = if (vm.isGroup && !m.isMine) {
                        memberNames[m.senderId] ?: "?"
                    } else null
                    MessageBubble(
                        message = m,
                        myId = myId,
                        senderName = senderName,
                        onLongPress = { menuFor = it },
                        onReactionTap = { msg, emoji -> vm.react(msg, emoji) },
                        onReplyTap = { targetId ->
                            val idx = messages.indexOfFirst { it.msgId == targetId }
                            if (idx >= 0) {
                                uiScope.launch { listState.animateScrollToItem(idx) }
                            }
                        },
                    )
                }
            }

            // Reply preview bar
            replyingTo?.let { r ->
                Surface(color = Charcoal700, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Replying to ${if (r.isMine) "yourself" else "message"}",
                                color = Emerald400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text(r.body.take(60), color = InkMid, fontSize = 13.sp, maxLines = 1)
                        }
                        IconButton(onClick = { vm.cancelReply() }) {
                            Icon(Icons.Filled.Close, "cancel reply", tint = InkMid)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Charcoal700,
                    modifier = Modifier.weight(1f),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkHigh),
                        cursorBrush = SolidColor(Emerald400),
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text("Message", color = InkLow, style = MaterialTheme.typography.bodyLarge)
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = if (draft.isNotBlank()) Emerald500 else Charcoal700,
                    modifier = Modifier.clickable(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            vm.send(draft)
                            draft = ""
                        },
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "send",
                        tint = if (draft.isNotBlank()) Charcoal950 else InkLow,
                        modifier = Modifier.padding(13.dp),
                    )
                }
            }
        }
    }

    // Long-press action dialog: quick reactions + reply/forward/copy
    menuFor?.let { m ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text("Message", fontSize = 16.sp) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        QuickEmojis.forEach { emoji ->
                            Text(
                                emoji,
                                fontSize = 26.sp,
                                modifier = Modifier.clickable {
                                    vm.react(m, emoji)
                                    menuFor = null
                                }.padding(4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    MenuRow("↩️  Reply") { vm.startReply(m); menuFor = null }
                    MenuRow("➡️  Forward") { forwardFor = m; menuFor = null }
                    MenuRow("📋  Copy") {
                        clipboard.setText(AnnotatedString(m.body))
                        menuFor = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuFor = null }) { Text("Close") }
            },
        )
    }

    // Forward picker
    forwardFor?.let { m ->
        val chats by chatsVm.chats.collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { forwardFor = null },
            title = { Text("Forward to…") },
            text = {
                LazyColumn {
                    items(chats.filter { it.conversationId != conversationId }, key = { it.conversationId }) { row ->
                        val gName by if (row.isGroup) {
                            chatsVm.groupName(row.conversationId.removePrefix("group:"))
                                .collectAsState(initial = null)
                        } else {
                            remember { mutableStateOf<String?>(null) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    vm.forwardMessage(m, row.conversationId)
                                    forwardFor = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (row.isGroup) {
                                Icon(Icons.Filled.Group, null, tint = Emerald400)
                            } else {
                                Avatar(name = row.name, size = 36.dp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (row.isGroup) gName ?: "Group" else row.name,
                                color = InkHigh,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { forwardFor = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        fontSize = 15.sp,
        color = InkHigh,
    )
}
