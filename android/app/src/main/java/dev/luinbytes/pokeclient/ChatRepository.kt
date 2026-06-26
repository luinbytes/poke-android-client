package dev.luinbytes.pokeclient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class ChatRepository {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun add(message: ChatMessage) {
        _messages.update { messages ->
            if (messages.none { it.id == message.id }) messages + message
            else messages.map { if (it.id == message.id) message else it }
        }
    }

    fun replace(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { messages -> messages.map { if (it.id == id) transform(it) else it } }
    }

    fun replaceId(oldId: String, newId: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { messages ->
            messages.mapNotNull { message ->
                when (message.id) {
                    oldId -> transform(message).copy(id = newId)
                    newId -> null
                    else -> message
                }
            }
        }
    }

    fun find(id: String): ChatMessage? = _messages.value.firstOrNull { it.id == id }

    fun clear() {
        _messages.value = emptyList()
    }

    fun outboundDraft(text: String): ChatMessage = ChatMessage(
        id = UUID.randomUUID().toString(),
        text = text,
        direction = MessageDirection.Outbound,
        status = MessageStatus.Sending,
        createdAt = System.currentTimeMillis()
    ).also(::add)
}
