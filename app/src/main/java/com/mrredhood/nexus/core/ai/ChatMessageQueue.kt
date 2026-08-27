package com.mrredhood.nexus.core.ai

import java.util.UUID

/**
 * FIFO queue for user messages submitted while Nexus is generating a response.
 * The queue is intentionally small and deterministic so rapid sends cannot
 * start concurrent model generations or lose user input.
 */
data class QueuedChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val context: ChatContext = ChatContext()
)

class ChatMessageQueue(private val maxSize: Int = 20) {
    private val items = ArrayDeque<QueuedChatMessage>()

    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    fun offer(message: QueuedChatMessage): Boolean {
        if (message.text.isBlank() || items.size >= maxSize) return false
        items.addLast(message)
        return true
    }

    fun poll(): QueuedChatMessage? = if (items.isEmpty()) null else items.removeFirst()

    fun clear() = items.clear()

    fun snapshot(): List<QueuedChatMessage> = items.toList()
}
