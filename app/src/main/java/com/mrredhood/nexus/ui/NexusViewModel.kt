package com.mrredhood.nexus.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.data.ProjectRepository
import com.mrredhood.nexus.data.WorkspaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class NexusViewModel(
    private val projectRepository: ProjectRepository,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {
    val projects = projectRepository.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workspaces = workspaceRepository.workspaces.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createProject(name: String, repositoryName: String?, workspaceUri: Uri, workspaceDisplayName: String, onCreated: (String) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val projectId = UUID.randomUUID().toString()
            val workspaceId = UUID.randomUUID().toString()
            workspaceRepository.takePersistablePermission(workspaceUri)
            workspaceRepository.save(Workspace(workspaceId, projectId, workspaceDisplayName.ifBlank { cleanName }, workspaceUri.toString()))
            projectRepository.save(NexusProject(projectId, cleanName, repositoryName?.trim()?.ifBlank { null }, workspaceId = workspaceId))
            onCreated(projectId)
        }
    }

    fun deleteProject(id: String) = viewModelScope.launch {
        workspaceRepository.removeForProject(id)
        projectRepository.delete(id)
    }

    fun workspaceForProject(projectId: String): Workspace? = workspaces.value.firstOrNull { it.projectId == projectId }
}

class NexusViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NexusViewModel::class.java)) {
            return NexusViewModel(ProjectRepository(context), WorkspaceRepository(context)) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
