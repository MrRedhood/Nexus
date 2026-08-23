package com.mrredhood.nexus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mrredhood.nexus.data.ProjectRepository

class NexusViewModelFactory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NexusViewModel(repository) as T
}
