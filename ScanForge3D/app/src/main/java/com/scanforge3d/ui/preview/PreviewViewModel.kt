package com.scanforge3d.ui.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge3d.data.model.TriangleMesh
import com.scanforge3d.data.repository.ScanRepository
import com.scanforge3d.processing.MeshProcessingPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanRepository: ScanRepository,
    private val pipeline: MeshProcessingPipeline
) : ViewModel() {

    data class PreviewState(
        val isLoading: Boolean = true,
        val processingStep: String = "",
        val processingProgress: Float = 0f,
        val mesh: TriangleMesh? = null,
        val error: String? = null
    )

    private val scanId: String = savedStateHandle["scanId"] ?: ""
    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state

    init {
        if (scanId.isNotEmpty()) {
            loadAndProcess()
        }
    }

    private fun loadAndProcess() {
        viewModelScope.launch {
            val pointsFlat = scanRepository.loadPointCloud(scanId)
            if (pointsFlat == null) {
                _state.value = PreviewState(isLoading = false, error = "Punktwolke nicht gefunden")
                return@launch
            }

            try {
                val result = pipeline.process(
                    pointsFlat = pointsFlat,
                    callback = object : MeshProcessingPipeline.ProgressCallback {
                        override fun onProgress(step: String, progress: Float) {
                            _state.value = _state.value.copy(
                                processingStep = step,
                                processingProgress = progress
                            )
                        }
                    }
                )

                scanRepository.saveMeshData(scanId, result.meshData)

                _state.value = PreviewState(
                    isLoading = false,
                    mesh = TriangleMesh.fromSerializedData(result.meshData)
                )
            } catch (e: Exception) {
                _state.value = PreviewState(
                    isLoading = false,
                    error = "Verarbeitung fehlgeschlagen: ${e.message}"
                )
            }
        }
    }
}
