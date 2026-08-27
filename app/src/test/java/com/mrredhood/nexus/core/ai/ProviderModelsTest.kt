package com.mrredhood.nexus.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModelsTest {
    @Test
    fun usage_calculatesTotalAndContextFraction() {
        val usage = AiUsage(inputTokens = 2_000, outputTokens = 500, totalTokens = 2_500, contextWindow = 10_000)

        assertEquals(2_500, usage.totalTokens)
        assertEquals(0.2, usage.contextUsedFraction!!, 0.0001)
    }

    @Test
    fun usage_withoutContextWindow_hasNoFraction() {
        val usage = AiUsage(inputTokens = 100, outputTokens = 50)

        assertNull(usage.contextUsedFraction)
    }

    @Test
    fun request_preservesOrderedFallbackModels() {
        val request = AiRequest(
            messages = listOf(AiMessage("user", "hello")),
            model = "primary",
            fallbackModels = listOf("fallback-one", "fallback-two")
        )

        assertEquals(listOf("fallback-one", "fallback-two"), request.fallbackModels)
    }

    @Test
    fun providerError_marksRetryableFailures() {
        val error = AiError("rate_limited", "Too many requests", retryable = true, httpStatus = 429)

        assertTrue(error.retryable)
        assertEquals(429, error.httpStatus)
    }
}
