package com.mrredhood.nexus.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceEntry
import com.mrredhood.nexus.core.workspace.WorkspaceFile
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import com.mrredhood.nexus.core.workspace.WorkspaceSearch
import com.mrredhood.nexus.core.workspace.WorkspaceSearchOptions
import com.mrredhood.nexus.core.workspace.WorkspaceSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkspaceViewModel(context: Context) : ViewModel() {
    private val fileSystem = WorkspaceFileSystem(context.applicationContext)
    private val searchService = WorkspaceSearch(fileSystem)
    private var searchJob: Job? = null
    private var navigationJob: Job? = null

    private val _entries = MutableStateFlow<List<WorkspaceEntry>>(emptyList())
    val entries: StateFlow<List<WorkspaceEntry>> = _entries.asStateFlow()
    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()
    private val _navigationStack = MutableStateFlow<List<String>>(emptyList())
    val navigationStack: StateFlow<List<String>> = _navigationStack.asStateFlow()
    private val _recentPaths = MutableStateFlow<List<String>>(emptyList())
    val recentPaths: StateFlow<List<String>> = _recentPaths.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _openedFile = MutableStateFlow<WorkspaceFile?>(null)
    val openedFile: StateFlow<WorkspaceFile?> = _openedFile.asStateFlow()
    private val _editorContent = MutableStateFlow<String?>(null)
    val editorContent: StateFlow<String?> = _editorContent.asStateFlow()
    private val _editorPath = MutableStateFlow<String?>(null)
    val editorPath: StateFlow<String?> = _editorPath.asStateFlow()
    private val _editorDirty = MutableStateFlow(false)
    val editorDirty: StateFlow<Boolean> = _editorDirty.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _searchResults = MutableStateFlow<List<WorkspaceSearchResult>>(emptyList())
    val searchResults: StateFlow<List<WorkspaceSearchResult>> = _searchResults.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun open(workspace: Workspace) {
        navigationJob?.cancel()
        _currentPath.value = ""
        _navigationStack.value = emptyList()
        _recentPaths.value = emptyList()
        load(workspace, "", recordNavigation = false)
    }

    fun enter(workspace: Workspace, relativePath: String) = navigateTo(workspace, relativePath)

    fun navigateTo(workspace: Workspace, relativePath: String) {
        val target = normalizePath(relativePath)
        val current = _currentPath.value
        if (target == current) {
            refresh(workspace)
            return
        }
        val stack = _navigationStack.value
        val newStack = when {
            target.isEmpty() -> emptyList()
            stack.lastOrNull() == target -> stack.dropLast(1)
            else -> (stack + current).filter { it.isNotEmpty() }.distinct()
        }
        load(workspace, target, recordNavigation = false, navigationStack = newStack)
    }

    fun back(workspace: Workspace): Boolean {
        val stack = _navigationStack.value
        if (stack.isNotEmpty()) {
            val target = stack.last()
            load(workspace, target, recordNavigation = false, navigationStack = stack.dropLast(1))
            return true
        }
        val current = _currentPath.value
        if (current.isNotEmpty()) {
            val parent = current.substringBeforeLast('/', "")
            load(workspace, parent, recordNavigation = false, navigationStack = emptyList())
            return true
        }
        return false
    }

    fun up(workspace: Workspace): Boolean {
        val current = _currentPath.value
        if (current.isEmpty()) return false
        val parent = current.substringBeforeLast('/', "")
        val stack = _navigationStack.value.filter { it != parent && it != current }
        load(workspace, parent, recordNavigation = false, navigationStack = stack)
        return true
    }

    fun refresh(workspace: Workspace) = load(workspace, _currentPath.value, recordNavigation = false, navigationStack = _navigationStack.value)

    fun search(workspace: Workspace, options: WorkspaceSearchOptions) {
        searchJob?.cancel()
        _searchQuery.value = options.query
        _searching.value = true
        _error.value = null
        searchJob = viewModelScope.launch {
            try { _searchResults.value = searchService.search(workspace, options) }
            catch (_: CancellationException) { throw CancellationException() }
            catch (e: Exception) { _error.value = e.message ?: "Search failed" }
            finally { _searching.value = false }
        }
    }

    fun clearSearch() {
        searchJob?.cancel(); searchJob = null
        _searchQuery.value = ""; _searchResults.value = emptyList(); _searching.value = false
    }

    fun read(workspace: Workspace, relativePath: String) {
        viewModelScope.launch {
            runCatching { fileSystem.read(workspace, relativePath) }
                .onSuccess { file ->
                    _openedFile.value = file; _editorPath.value = file.relativePath
                    _editorContent.value = file.content; _editorDirty.value = false
                }
                .onFailure { _error.value = it.message ?: "Unable to read file" }
        }
    }

    fun updateEditorContent(content: String) { if (_editorContent.value != null) { _editorContent.value = content; _editorDirty.value = true } }

    fun saveEditor(workspace: Workspace) {
        val path = _editorPath.value ?: return; val content = _editorContent.value ?: return
        if (!_editorDirty.value) return
        viewModelScope.launch {
            _saving.value = true; _error.value = null
            runCatching { fileSystem.write(workspace, path, content, mimeTypeFor(path)) }
                .onSuccess { file -> _openedFile.value = file; _editorContent.value = file.content; _editorDirty.value = false }
                .onFailure { _error.value = it.message ?: "Unable to save file" }
            _saving.value = false
        }
    }

    fun clearEditor() { _openedFile.value = null; _editorPath.value = null; _editorContent.value = null; _editorDirty.value = false }
    fun clearOpenedFile() = clearEditor()
    fun clearError() { _error.value = null }

    fun createFile(workspace: Workspace, name: String) = mutateAndRefresh(workspace) { fileSystem.createFile(workspace, join(_currentPath.value, name), mimeTypeFor(name)) }
    fun createDirectory(workspace: Workspace, name: String) = mutateAndRefresh(workspace) { fileSystem.createDirectory(workspace, join(_currentPath.value, name)) }
    fun delete(workspace: Workspace, path: String) = mutateAndRefresh(workspace) { fileSystem.delete(workspace, path) }
    fun rename(workspace: Workspace, path: String, name: String) = mutateAndRefresh(workspace) { fileSystem.rename(workspace, path, name) }
    fun copy(workspace: Workspace, source: String, destination: String) = mutateAndRefresh(workspace) { fileSystem.copy(workspace, source, destination) }
    fun move(workspace: Workspace, source: String, destination: String) = mutateAndRefresh(workspace) { fileSystem.move(workspace, source, destination) }

    private fun load(workspace: Workspace, path: String, recordNavigation: Boolean, navigationStack: List<String> = _navigationStack.value) {
        val normalized = normalizePath(path)
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch {
            _loading.value = true; _error.value = null
            runCatching { fileSystem.list(workspace, normalized) }
                .onSuccess {
                    _currentPath.value = normalized
                    _navigationStack.value = navigationStack.filter { it != normalized }.takeLast(50)
                    _recentPaths.value = (_recentPaths.value.filter { it != normalized } + normalized).takeLast(20)
                    _entries.value = it
                }
                .onFailure { _error.value = it.message ?: "Unable to load workspace" }
            _loading.value = false
        }
    }

    private fun mutateAndRefresh(workspace: Workspace, operation: suspend () -> Any) {
        viewModelScope.launch {
            _error.value = null
            runCatching { operation() }
                .onFailure { _error.value = it.message ?: "Operation failed" }
                .onSuccess { load(workspace, _currentPath.value, false, _navigationStack.value) }
        }
    }

    private fun normalizePath(path: String): String {
        val clean = path.trim().replace('\\', '/')
        require(!clean.startsWith('/') && clean.split('/').none { it == "." || it == ".." || it.contains('\u0000') }) { "Invalid workspace path" }
        return clean.split('/').filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts", "java", "groovy" -> "text/x-kotlin"
        "js", "mjs", "cjs", "ts", "tsx", "jsx" -> "text/javascript"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "md", "markdown", "txt", "gradle", "properties", "yaml", "yml", "toml", "sh" -> "text/plain"
        else -> "text/plain"
    }

    private fun join(parent: String, child: String): String {
        val cleanChild = child.trim().replace('\\', '/').trim('/')
        if (parent.isBlank()) return cleanChild
        val cleanParent = parent.trim('/').replace('\\', '/')
        return if (cleanChild == cleanParent || cleanChild.startsWith("$cleanParent/")) cleanChild else "$cleanParent/$cleanChild"
    }
}

class WorkspaceViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) return WorkspaceViewModel(context) as T
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
