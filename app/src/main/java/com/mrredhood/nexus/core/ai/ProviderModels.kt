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
    val stream: Boolean = false,
    /** Optional same-provider fallback models, tried in order after a request failure. */
    val fallbackModels: List<String> = emptyList()
)

data class AiUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val contextWindow: Int? = null
) {
    val contextUsedFraction: Double?
        get() = if (contextWindow != null && inputTokens != null && contextWindow > 0) inputTokens.toDouble() / contextWindow else null
}

data class AiError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val httpStatus: Int? = null
)

data class AiResponse(
    val text: String,
    val model: String,
    val provider: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val contextWindow: Int? = null,
    val error: AiError? = null
) {
    val usage: AiUsage get() = AiUsage(inputTokens, outputTokens, totalTokens, contextWindow)
}

data class ProviderResult(
    val success: Boolean,
    val message: String,
    val response: AiResponse? = null,
    val error: AiError? = null,
    val attempts: Int = 1
)
