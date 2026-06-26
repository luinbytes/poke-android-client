package dev.luinbytes.pokeclient

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRepositoryTest {
    @Test
    fun deduplicatesMessagesById() {
        val repo = ChatRepository()
        val message = ChatMessage(
            id = "same",
            text = "hello",
            direction = MessageDirection.Inbound,
            status = MessageStatus.Received,
            createdAt = 1
        )

        repo.add(message)
        repo.add(message.copy(text = "ignored"))

        assertEquals(1, repo.messages.value.size)
        assertEquals("hello", repo.messages.value.single().text)
    }
}
