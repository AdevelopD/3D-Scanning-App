package com.scanforge3d.ui.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge3d.data.model.TriangleMesh
import com.scanforge3d.data.repository.ScanRepository
import com.scanforge3d.export.CloudExportManager
import com.scanforge3d.export.STLExporter
import com.scanforge3d.export.OBJExporter
import com.scanforge3d.export.PLYExporter
import com.scanforge3d.processing.NativeMeshProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanRepository: ScanRepository,
    private val stlExporter: STLExporter,
    private val objExporter: OBJExporter,
    private val plyExporter: PLYExporter,
    private val cloudExportManager: CloudExportManager,
    private val nativeMeshProcessor: NativeMeshProcessor
) : ViewModel() {

    data class ExportState(
        val vertexCount: Int = 0,
        val triangleCount: Int = 0,
        val isWatertight: Boolean = true,
        val estimatedFileSize: String = "---",
        val stlExporting: Boolean = false,
        val stlCompleted: Boolean = false,
        val objExporting: Boolean = false,
        val objCompleted: Boolean = false,
        val plyExporting: Boolean = false,
        val plyCompleted: Boolean = false,
        val stepExporting: Boolean = false,
        val stepCompleted: Boolean = false,
        val stepProgress: Float = 0f,
        val error: String? = null
    )

    private val scanId: String = savedStateHandle["scanId"] ?: ""
    private val _exportState = MutableStateFlow(ExportState())
    val exportState: StateFlow<ExportState> = _exportState

    private var meshData: FloatArray? = null

    init {
        loadMeshData()
    }

    private fun loadMeshData() {
        viewModelScope.launch {
            meshData = scanRepository.loadMeshData(scanId)
            meshData?.let { data ->
                val mesh = TriangleMesh.fromSerializedData(data)
                _exportState.value = _exportState.value.copy(
                    vertexCount = mesh.vertexCount,
                    triangleCount = mesh.triangleCount,
                    estimatedFileSize = formatFileSize(mesh.estimateFileSizeSTL())
                )
            }
        }
    }

    fun exportSTL(scanId: String) {
        val data = meshData ?: return
        _exportState.value = _exportState.value.copy(stlExporting = true)
        viewModelScope.launch {
            val uri = stlExporter.exportToDownloads(data, "scan_${scanId}.stl")
            _exportState.value = _exportState.value.copy(
                stlExporting = false,
                stlCompleted = uri != null,
                error = if (uri == null) "STL-Export fehlgeschlagen" else null
            )
        }
    }

    fun exportOBJ(scanId: String) {
        val data = meshData ?: return
        _exportState.value = _exportState.value.copy(objExporting = true)
        viewModelScope.launch {
            val file = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ),
                "scan_${scanId}.obj"
            )
            val success = objExporter.exportToFile(data, file)
            _exportState.value = _exportState.value.copy(
                objExporting = false,
                objCompleted = success,
                error = if (!success) "OBJ-Export fehlgeschlagen" else null
            )
        }
    }

    fun exportSTEP(scanId: String) {
        val data = meshData ?: return
        _exportState.value = _exportState.value.copy(stepExporting = true)
        viewModelScope.launch {
            // First export STL locally, then upload to cloud
            val tempFile = File.createTempFile("scan_", ".stl")
            nativeMeshProcessor.exportSTL(data, tempFile.absolutePath)

            cloudExportManager.exportAsSTEP(tempFile).collect { state ->
                when (state) {
                    is CloudExportManager.ExportState.Uploading -> {
                        _exportState.value = _exportState.value.copy(stepProgress = 0.1f)
                    }
                    is CloudExportManager.ExportState.Processing -> {
                        _exportState.value = _exportState.value.copy(stepProgress = state.progress)
                    }
                    is CloudExportManager.ExportState.Completed -> {
                        _exportState.value = _exportState.value.copy(
                            stepExporting = false,
                            stepCompleted = true
                        )
                    }
                    is CloudExportManager.ExportState.Error -> {
                        _exportState.value = _exportState.value.copy(
                            stepExporting = false,
                            error = state.message
                        )
                    }
                }
            }
            tempFile.delete()
        }
    }

    fun downloadCATIAMacro() {
        // Opens browser or share dialog with CATIA macro info
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
