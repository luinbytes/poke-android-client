package dev.luinbytes.pokeclient

import kotlinx.serialization.Serializable

enum class MessageDirection { Outbound, Inbound, System }
enum class MessageStatus { Draft, Sending, Sent, Failed, Received }

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val direction: MessageDirection,
    val status: MessageStatus,
    val createdAt: Long,
    val actions: List<RichAction> = emptyList()
)

@Serializable
data class RichAction(
    val id: String,
    val type: String,
    val label: String,
    val payload: Map<String, String> = emptyMap()
)

data class AppSettings(
    val pokeApiKey: String = "",
    val backendBaseUrl: String = "",
    val pokeUserId: String = ""
)

data class SendResult(
    val ok: Boolean,
    val message: String? = null
)
