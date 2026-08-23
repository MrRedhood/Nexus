package com.mrredhood.nexus.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceEntry
import com.mrredhood.nexus.core.workspace.WorkspaceFile
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkspaceViewModel(context: Context) : ViewModel() {
    private val fileSystem = WorkspaceFileSystem(context.applicationContext)
    private val _entries = MutableStateFlow<List<WorkspaceEntry>>(emptyList())
    val entries: StateFlow<List<WorkspaceEntry>> = _entries.asStateFlow()
    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _openedFile = MutableStateFlow<WorkspaceFile?>(null)
    val openedFile: StateFlow<WorkspaceFile?> = _openedFile.asStateFlow()

    fun open(workspace: Workspace) {
        _currentPath.value = ""
        load(workspace, "")
    }

    fun enter(workspace: Workspace, relativePath: String) {
        load(workspace, relativePath)
    }

    fun up(workspace: Workspace) {
        val current = _currentPath.value
        val parent = current.substringBeforeLast('/', "")
        load(workspace, parent)
    }

    fun refresh(workspace: Workspace) = load(workspace, _currentPath.value)

    fun read(workspace: Workspace, relativePath: String) {
        viewModelScope.launch {
            runCatching { fileSystem.read(workspace, relativePath) }
                .onSuccess { _openedFile.value = it }
                .onFailure { _error.value = it.message ?: "Unable to read file" }
        }
    }

    fun clearOpenedFile() { _openedFile.value = null }
    fun clearError() { _error.value = null }

    fun createFile(workspace: Workspace, name: String) = mutate { fileSystem.createFile(workspace, join(_currentPath.value, name)) }
    fun createDirectory(workspace: Workspace, name: String) = mutate { fileSystem.createDirectory(workspace, join(_currentPath.value, name)) }
    fun delete(workspace: Workspace, path: String) = mutate { fileSystem.delete(workspace, path).let { Unit } }
    fun rename(workspace: Workspace, path: String, name: String) = mutate { fileSystem.rename(workspace, path, name) }

    private fun load(workspace: Workspace, path: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { fileSystem.list(workspace, path) }
                .onSuccess {
                    _currentPath.value = path
                    _entries.value = it
                }
                .onFailure { _error.value = it.message ?: "Unable to load workspace" }
            _loading.value = false
        }
    }

    private fun mutate(operation: suspend () -> Any) {
        viewModelScope.launch {
            _error.value = null
            runCatching { operation() }.onFailure { _error.value = it.message ?: "Operation failed" }
        }
    }

    private fun join(parent: String, child: String): String = if (parent.isBlank()) child.trim() else "${parent.trimEnd('/')}/${child.trim()}"
}

class WorkspaceViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) return WorkspaceViewModel(context) as T
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
