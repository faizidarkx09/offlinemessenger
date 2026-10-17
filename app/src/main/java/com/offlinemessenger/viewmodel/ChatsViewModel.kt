package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ChatRow(
    val conversationId: String,
    val name: String,
    val lastBody: String,
    val timestampMs: Long,
    val isMine: Boolean,
    val status: MessageStatus,
    val verified: Boolean,
)

class ChatsViewModel(locator: ServiceLocator) : ViewModel() {
    private val repo = locator.chatRepository
    private val peers = locator.peerRepository

    val chats: Flow<List<ChatRow>> =
        combine(repo.latestPerConversation(), peers.contacts) { latest, contacts ->
            val byId = contacts.associateBy { it.deviceId }
            latest.map { m: MessageEntity ->
                val c = byId[m.conversationId]
                ChatRow(
                    conversationId = m.conversationId,
                    name = c?.displayName ?: "Unknown (${m.conversationId.take(8)})",
                    lastBody = m.body,
                    timestampMs = m.timestampMs,
                    isMine = m.isMine,
                    status = m.status,
                    verified = c?.verified == true,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unreadCount(conversationId: String): Flow<Int> = repo.unreadCount(conversationId)
}
