package com.scanforge3d.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge3d.data.model.ScanProject
import com.scanforge3d.data.repository.ProjectRepository
import com.scanforge3d.data.repository.ScanRepository
import com.scanforge3d.scanning.ScanSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.ar.core.Frame
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanSessionController: ScanSessionController,
    private val scanRepository: ScanRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    data class ScanUiState(
        val scanId: String = "",
        val isScanning: Boolean = false,
        val pointCount: Int = 0,
        val frameCount: Int = 0,
        val surfaceCoverage: Float = 0f,
        val qualityScore: Float = 0f,
        val qualityLabel: String = "---"
    )

    private val _scanState = MutableStateFlow(ScanUiState())
    val scanState: StateFlow<ScanUiState> = _scanState

    init {
        viewModelScope.launch {
            scanSessionController.state.collect { state ->
                _scanState.value = ScanUiState(
                    scanId = state.scanId,
                    isScanning = state.isScanning,
                    pointCount = state.pointCount,
                    frameCount = state.frameCount,
                    surfaceCoverage = state.surfaceCoverage,
                    qualityScore = state.qualityScore,
                    qualityLabel = state.qualityLabel
                )
            }
        }
    }

    fun onArFrameUpdate(frame: Frame) {
        scanSessionController.processFrame(frame)
    }

    fun startScan() {
        scanSessionController.startScan()
    }

    fun stopScan() {
        val points = scanSessionController.stopScan()
        val scanId = scanSessionController.getScanId()

        viewModelScope.launch {
            // Flatten and save point cloud
            val flat = FloatArray(points.size * 3)
            points.forEachIndexed { i, p ->
                flat[i * 3] = p[0]
                flat[i * 3 + 1] = p[1]
                flat[i * 3 + 2] = p[2]
            }
            scanRepository.savePointCloud(scanId, flat)

            // Create project entry
            val project = ScanProject(
                id = scanId,
                name = "Scan ${System.currentTimeMillis()}",
                pointCount = points.size
            )
            projectRepository.saveProject(project)
        }
    }
}
