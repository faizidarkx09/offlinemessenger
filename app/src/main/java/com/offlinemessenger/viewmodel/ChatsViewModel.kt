package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.di.ServiceLocator
import com.offlinemessenger.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatRow(
    val conversationId: String,
    val name: String,
    val lastBody: String,
    val timestampMs: Long,
    val isMine: Boolean,
    val status: MessageStatus,
    val verified: Boolean,
    val pinned: Boolean,
    val isGroup: Boolean,
    val unread: Int,
)

class ChatsViewModel(locator: ServiceLocator) : ViewModel() {
    private val repo = locator.chatRepository
    private val peers = locator.peerRepository

    /** Search query for filtering the chat list. */
    val searchQuery = MutableStateFlow("")

    val chats: Flow<List<ChatRow>> =
        combine(
            repo.latestPerConversation(),
            peers.contacts,
            repo.conversationPrefs(),
            searchQuery,
        ) { latest, contacts, prefs, query ->
            val byId = contacts.associateBy { it.deviceId }
            val pinById = prefs.associate { it.conversationId to it.pinned }
            val q = query.trim().lowercase()
            latest.mapNotNull { m: MessageEntity ->
                val c = byId[m.conversationId]
                val isGroup = m.conversationId.startsWith(ChatRepository.GROUP_PREFIX)
                val name = when {
                    isGroup -> null // resolved below via group flow; placeholder for now
                    else -> c?.displayName ?: "Unknown (${m.conversationId.take(8)})"
                }
                if (q.isNotEmpty()) {
                    val hay = ((name ?: "") + " " + m.body).lowercase()
                    if (!hay.contains(q)) return@mapNotNull null
                }
                ChatRow(
                    conversationId = m.conversationId,
                    name = name ?: m.conversationId,
                    lastBody = m.body,
                    timestampMs = m.timestampMs,
                    isMine = m.isMine,
                    status = m.status,
                    verified = c?.verified == true,
                    pinned = pinById[m.conversationId] == true,
                    isGroup = isGroup,
                    unread = 0, // filled per-row by the screen via unreadCount()
                )
            }.sortedWith(compareByDescending<ChatRow> { it.pinned }.thenByDescending { it.timestampMs })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unreadCount(conversationId: String): Flow<Int> = repo.unreadCount(conversationId)

    fun groupName(groupId: String): Flow<String?> =
        repo.group(groupId).map { it?.name }

    fun togglePin(conversationId: String, pinned: Boolean) {
        viewModelScope.launch { repo.setPinned(conversationId, pinned) }
    }

    fun searchMessages(q: String, onResult: (List<MessageEntity>) -> Unit) {
        viewModelScope.launch { onResult(repo.searchMessages(q)) }
    }
}
