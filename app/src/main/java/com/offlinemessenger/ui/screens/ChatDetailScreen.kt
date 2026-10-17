package com.offlinemessenger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.components.MessageBubble
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.viewmodel.ChatDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(conversationId: String, onBack: () -> Unit) {
    val locator = rememberLocator()
    val vm: ChatDetailViewModel = viewModel(
        key = "chat-$conversationId",
        factory = VmFactory { ChatDetailViewModel(locator, conversationId) },
    )
    val messages by vm.messages.collectAsState(initial = emptyList())
    val contact by vm.contact.collectAsState(initial = null)
    val sendError by vm.sendError.collectAsState(initial = null)
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(sendError) {
        sendError?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = contact?.displayName ?: "?", size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    contact?.displayName ?: "Unknown peer",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (contact?.verified == true) {
                                    Icon(
                                        Icons.Filled.Verified, "verified",
                                        tint = Emerald400,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                            Text(
                                if (contact == null) "key not exchanged yet"
                                else "end-to-end encrypted",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMid,
                            )
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
                items(messages, key = { it.msgId }) { m -> MessageBubble(m) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    singleLine = false,
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            vm.send(draft)
                            draft = ""
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "send", tint = Emerald400)
                }
            }
        }
    }
}
