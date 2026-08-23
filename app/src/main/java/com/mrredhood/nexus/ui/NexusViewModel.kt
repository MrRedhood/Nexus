package com.mrredhood.nexus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.model.NexusProject
import com.mrredhood.nexus.data.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class NexusViewModel(private val repository: ProjectRepository) : ViewModel() {
    val projects = repository.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createProject(name: String, repositoryName: String? = null) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.save(NexusProject(UUID.randomUUID().toString(), clean, repositoryName))
        }
    }

    fun deleteProject(id: String) = viewModelScope.launch { repository.delete(id) }
}
