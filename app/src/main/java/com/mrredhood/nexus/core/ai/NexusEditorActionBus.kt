package com.mrredhood.nexus.core.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Small process-local bridge from AI action cards to the active workspace editor. */
object NexusEditorActionBus {
    private val _requests = MutableSharedFlow<NexusEditorActionRequest>(extraBufferCapacity = 32)
    val requests: SharedFlow<NexusEditorActionRequest> = _requests.asSharedFlow()

    fun request(workspaceId: String, path: String, focus: Boolean) {
        _requests.tryEmit(NexusEditorActionRequest(workspaceId, path, focus))
    }
}

data class NexusEditorActionRequest(
    val workspaceId: String,
    val path: String,
    val focus: Boolean
)
