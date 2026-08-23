package com.mrredhood.nexus.core.ai

/** Provider-neutral request/response models used by Nexus AI services. */
data class AiMessage(
    val role: String,
    val content: String
)

data class AiRequest(
    val messages: List<AiMessage>,
    val model: String,
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 4096,
    val stream: Boolean = false
)

data class AiResponse(
    val text: String,
    val model: String,
    val provider: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
)

data class ProviderResult(
    val success: Boolean,
    val message: String,
    val response: AiResponse? = null
)
