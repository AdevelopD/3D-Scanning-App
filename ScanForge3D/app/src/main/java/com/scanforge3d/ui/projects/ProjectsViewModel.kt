package com.scanforge3d.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge3d.data.model.ScanProject
import com.scanforge3d.data.repository.ProjectRepository
import com.scanforge3d.data.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _projects = MutableStateFlow<List<ScanProject>>(emptyList())
    val projects: StateFlow<List<ScanProject>> = _projects

    init {
        viewModelScope.launch {
            projectRepository.getAllProjects().collect { projects ->
                _projects.value = projects
            }
        }
    }

    fun deleteProject(project: ScanProject) {
        viewModelScope.launch {
            projectRepository.deleteProject(project.id)
            scanRepository.deleteScanData(project.id)
        }
    }
}
