package com.mrredhood.nexus.core.ai

/** Immutable review data shown before a mutating Nexus action is applied. */
data class NexusActionReview(
    val actionId: String,
    val path: String,
    val original: String,
    val proposed: String,
    val additions: Int,
    val deletions: Int,
    val diff: List<NexusDiffLine>
) {
    val changed: Boolean get() = original != proposed
}

enum class NexusDiffKind { CONTEXT, ADD, REMOVE }

data class NexusDiffLine(
    val kind: NexusDiffKind,
    val text: String
)

object NexusDiffBuilder {
    fun build(actionId: String, path: String, original: String, proposed: String): NexusActionReview {
        if (original == proposed) return NexusActionReview(actionId, path, original, proposed, 0, 0, emptyList())

        val oldLines = original.split('\n')
        val newLines = proposed.split('\n')
        var prefix = 0
        while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++

        var suffix = 0
        while (suffix < oldLines.size - prefix && suffix < newLines.size - prefix &&
            oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]) suffix++

        val result = mutableListOf<NexusDiffLine>()
        for (i in 0 until prefix) result += NexusDiffLine(NexusDiffKind.CONTEXT, oldLines[i])
        val oldEnd = oldLines.size - suffix
        val newEnd = newLines.size - suffix
        for (i in prefix until oldEnd) result += NexusDiffLine(NexusDiffKind.REMOVE, oldLines[i])
        for (i in prefix until newEnd) result += NexusDiffLine(NexusDiffKind.ADD, newLines[i])
        for (i in oldEnd until oldLines.size) result += NexusDiffLine(NexusDiffKind.CONTEXT, oldLines[i])

        return NexusActionReview(
            actionId, path, original, proposed,
            result.count { it.kind == NexusDiffKind.ADD },
            result.count { it.kind == NexusDiffKind.REMOVE },
            result
        )
    }
}
