package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatDetailViewModel(
    locator: ServiceLocator,
    val conversationId: String,
) : ViewModel() {
    private val repo = locator.chatRepository
    private val peers = locator.peerRepository

    val messages: Flow<List<MessageEntity>> = repo.conversation(conversationId)

    val contact: Flow<ContactEntity?> = peers.contacts
        .map { list -> list.firstOrNull { it.deviceId == conversationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sendError: Flow<String?> get() = _sendError
    private val _sendError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { repo.markRead(conversationId) }
    }

    fun send(text: String) {
        viewModelScope.launch {
            val id = runCatching { UUID.fromString(conversationId) }.getOrNull()
            if (id == null) {
                _sendError.value = "Invalid conversation."
                return@launch
            }
            repo.sendMessage(id, text)
                .onFailure { _sendError.value = it.message }
                .onSuccess { _sendError.value = null }
        }
    }

    fun clearError() {
        _sendError.value = null
    }
}
