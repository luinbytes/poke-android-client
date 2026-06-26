package dev.luinbytes.pokeclient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class ChatRepository {
    private val seen = mutableSetOf<String>()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun add(message: ChatMessage) {
        if (!seen.add(message.id)) return
        _messages.update { it + message }
    }

    fun replace(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { messages -> messages.map { if (it.id == id) transform(it) else it } }
    }

    fun clear() {
        seen.clear()
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
