package com.scanforge3d.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge3d.data.model.ScanProject
import com.scanforge3d.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    data class HomeState(
        val recentProjects: List<ScanProject> = emptyList(),
        val totalScans: Int = 0
    )

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        viewModelScope.launch {
            projectRepository.getAllProjects().collect { projects ->
                _state.value = HomeState(
                    recentProjects = projects.take(5),
                    totalScans = projects.size
                )
            }
        }
    }
}
