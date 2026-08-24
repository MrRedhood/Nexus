package com.mrredhood.nexus.core.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Small process-local bridge from AI actions to the active workspace editor. */
object NexusEditorActionBus {
    private val _requests = MutableSharedFlow<NexusEditorActionRequest>(extraBufferCapacity = 32)
    val requests: SharedFlow<NexusEditorActionRequest> = _requests.asSharedFlow()

    fun request(workspaceId: String, path: String, focus: Boolean) {
        // Opening a file is always safe; WorkspaceViewModel reads it when it is not already open.
        _requests.tryEmit(NexusEditorActionRequest(workspaceId, path, false))
    }
}

data class NexusEditorActionRequest(
    val workspaceId: String,
    val path: String,
    val focus: Boolean
)
