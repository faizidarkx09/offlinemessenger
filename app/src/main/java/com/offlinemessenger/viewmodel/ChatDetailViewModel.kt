package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.data.local.entity.GroupEntity
import com.offlinemessenger.data.local.entity.GroupMemberEntity
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.di.ServiceLocator
import com.offlinemessenger.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val isGroup: Boolean = conversationId.startsWith(ChatRepository.GROUP_PREFIX)
    private val groupId: String? =
        if (isGroup) conversationId.removePrefix(ChatRepository.GROUP_PREFIX) else null

    val messages: Flow<List<MessageEntity>> = repo.conversation(conversationId)

    val contact: Flow<ContactEntity?> = peers.contacts
        .map { list -> list.firstOrNull { it.deviceId == conversationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val group: StateFlow<GroupEntity?> = (
        if (groupId != null) repo.group(groupId)
        else kotlinx.coroutines.flow.flowOf(null)
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groupMembers: Flow<List<GroupMemberEntity>> =
        if (groupId != null) repo.groupMembers(groupId)
        else kotlinx.coroutines.flow.flowOf(emptyList())

    /** Message currently quoted as a reply (null = not replying). */
    val replyingTo = MutableStateFlow<MessageEntity?>(null)

    val sendError: Flow<String?> get() = _sendError
    private val _sendError = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { repo.markRead(conversationId) }
    }

    fun send(text: String) {
        val draft = text.trim()
        if (draft.isEmpty()) return
        viewModelScope.launch {
            val replyTo = replyingTo.value
            replyingTo.value = null
            val result = if (isGroup && groupId != null) {
                repo.sendGroupMessage(groupId, draft, replyTo)
            } else {
                val id = runCatching { UUID.fromString(conversationId) }.getOrNull()
                if (id == null) {
                    _sendError.value = "Invalid conversation."
                    return@launch
                }
                repo.sendMessage(id, draft, replyTo)
            }
            result
                .onFailure { _sendError.value = it.message }
                .onSuccess { _sendError.value = null }
        }
    }

    fun react(target: MessageEntity, emoji: String) {
        viewModelScope.launch {
            repo.sendReaction(target, emoji)
                .onFailure { _sendError.value = it.message }
        }
    }

    fun forwardMessage(original: MessageEntity, toConversationId: String) {
        viewModelScope.launch {
            val result = if (toConversationId.startsWith(ChatRepository.GROUP_PREFIX)) {
                repo.sendGroupMessage(
                    toConversationId.removePrefix(ChatRepository.GROUP_PREFIX),
                    original.body, forwarded = true,
                )
            } else {
                val id = runCatching { UUID.fromString(toConversationId) }.getOrNull()
                if (id == null) {
                    _sendError.value = "Invalid conversation."
                    return@launch
                }
                repo.sendMessage(id, original.body, forwarded = true)
            }
            result.onFailure { _sendError.value = it.message }
        }
    }

    fun startReply(m: MessageEntity) {
        replyingTo.value = m
    }

    fun cancelReply() {
        replyingTo.value = null
    }

    fun clearError() {
        _sendError.value = null
    }

    fun memberName(deviceId: String): String =
        if (deviceId == repo.myDeviceId.toString()) "You" else "…"
}
