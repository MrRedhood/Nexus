package com.mrredhood.nexus.core.ai

import java.util.UUID

/** FIFO queue for user messages submitted while Nexus is generating. */
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

    fun remove(id: String): Boolean {
        val item = items.firstOrNull { it.id == id } ?: return false
        return items.remove(item)
    }

    fun clear() = items.clear()

    fun snapshot(): List<QueuedChatMessage> = items.toList()
}
