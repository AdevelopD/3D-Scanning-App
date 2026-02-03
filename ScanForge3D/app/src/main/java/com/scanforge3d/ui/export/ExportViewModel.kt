package com.scanforge3d.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
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

    private val _shareIntent = MutableSharedFlow<Intent>()
    val shareIntent: SharedFlow<Intent> = _shareIntent

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
        viewModelScope.launch {
            val macroFile = File(appContext.cacheDir, "ScanForge_Import.CATScript")
            macroFile.writeText(CATIA_MACRO_SCRIPT)

            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                macroFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ScanForge3D CATIA Import-Macro")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "CATIA Import-Macro für ScanForge3D.\n" +
                    "In CATIA: Tools → Macro → Macros → Erstellen → Code einfügen → Ausführen"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            _shareIntent.emit(Intent.createChooser(shareIntent, "Macro teilen"))
        }
    }

    companion object {
        private val CATIA_MACRO_SCRIPT = """
            |' ScanForge_Import.CATScript
            |' Automatische STEP → CATPart Konvertierung
            |'
            |' Installation:
            |' 1. In CATIA: Tools → Macro → Macros...
            |' 2. "Erstellen" → Name: "ScanForge_Import"
            |' 3. Diesen Code einfügen
            |' 4. Ausführen mit Pfad zur STEP-Datei
            |
            |Sub CATMain()
            |
            |    Dim sStepFile As String
            |    Dim sOutputDir As String
            |
            |    ' Dateiauswahl-Dialog
            |    sStepFile = CATIA.FileSelectionBox( _
            |        "STEP-Datei auswählen", "*.stp;*.step", CatFileSelectionModeOpen)
            |
            |    If sStepFile = "" Then Exit Sub
            |
            |    ' STEP importieren
            |    Dim oDoc As Document
            |    Set oDoc = CATIA.Documents.Open(sStepFile)
            |
            |    ' Als CATPart speichern
            |    sOutputDir = Left(sStepFile, InStrRev(sStepFile, "\"))
            |    Dim sFileName As String
            |    sFileName = Mid(sStepFile, InStrRev(sStepFile, "\") + 1)
            |    sFileName = Left(sFileName, InStrRev(sFileName, ".") - 1)
            |
            |    Dim sOutputPath As String
            |    sOutputPath = sOutputDir & sFileName & ".CATPart"
            |
            |    oDoc.SaveAs sOutputPath
            |    MsgBox "Gespeichert als: " & sOutputPath, vbInformation, "ScanForge3D"
            |
            |End Sub
        """.trimMargin()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
