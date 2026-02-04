package com.scanforge3d.ui.photocapture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge3d.data.model.ScanProject
import com.scanforge3d.data.remote.CloudApiService
import com.scanforge3d.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PhotoCaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudApi: CloudApiService,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    data class PhotoCaptureState(
        val sessionId: String = UUID.randomUUID().toString(),
        val capturedPhotos: List<File> = emptyList(),
        val photoCount: Int = 0,
        val isUploading: Boolean = false,
        val uploadProgress: Float = 0f,
        val reconstructionProgress: Float = 0f,
        val processingStatus: String = "",
        val completedScanId: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(PhotoCaptureState())
    val state: StateFlow<PhotoCaptureState> = _state

    private val photoDir: File
        get() {
            val dir = File(context.cacheDir, "photos_${_state.value.sessionId}")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun onPhotoCaptured(file: File) {
        // Verify file was actually written by CameraX before adding
        if (!file.exists() || file.length() == 0L) return
        _state.update { current ->
            current.copy(
                capturedPhotos = current.capturedPhotos + file,
                photoCount = current.photoCount + 1
            )
        }
    }

    fun getPhotoOutputFile(): File {
        return File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
    }

    fun deletePhoto(index: Int) {
        _state.update { current ->
            if (index !in current.capturedPhotos.indices) return@update current
            val photo = current.capturedPhotos[index]
            photo.delete()
            val updated = current.capturedPhotos.toMutableList().apply { removeAt(index) }
            current.copy(
                capturedPhotos = updated,
                photoCount = updated.size
            )
        }
    }

    fun uploadAndReconstruct() {
        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isUploading = true,
                        uploadProgress = 0f,
                        error = null,
                        processingStatus = "Fotos hochladen..."
                    )
                }

                // Filter out any files that no longer exist
                val photos = _state.value.capturedPhotos.filter { it.exists() && it.length() > 0 }
                if (photos.isEmpty()) {
                    _state.update {
                        it.copy(isUploading = false, error = "Keine gültigen Fotos vorhanden")
                    }
                    return@launch
                }

                val parts = photos.mapIndexed { index, file ->
                    val requestBody = file.asRequestBody("image/jpeg".toMediaType())
                    _state.update {
                        it.copy(uploadProgress = (index + 1).toFloat() / photos.size * 0.5f)
                    }
                    MultipartBody.Part.createFormData(
                        "files", file.name, requestBody
                    )
                }

                _state.update {
                    it.copy(
                        uploadProgress = 0.5f,
                        processingStatus = "Upload abgeschlossen, starte Rekonstruktion..."
                    )
                }

                val response = cloudApi.uploadImages(parts)
                if (!response.isSuccessful) {
                    _state.update {
                        it.copy(
                            isUploading = false,
                            error = "Upload fehlgeschlagen: ${response.code()}"
                        )
                    }
                    return@launch
                }

                val jobId = response.body()?.job_id ?: run {
                    _state.update {
                        it.copy(isUploading = false, error = "Keine Job-ID erhalten")
                    }
                    return@launch
                }

                pollStatus(jobId)
            } catch (e: java.net.UnknownHostException) {
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = "Cloud-Backend nicht erreichbar. Bitte starte das Backend (cloud-backend/) zuerst."
                    )
                }
            } catch (e: java.net.ConnectException) {
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = "Keine Verbindung zum Cloud-Backend. Läuft der Server?"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = "Fehler: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun pollStatus(jobId: String) {
        _state.update {
            it.copy(processingStatus = "3D-Rekonstruktion läuft...")
        }

        var completed = false
        while (!completed) {
            delay(3000)

            try {
                val statusResponse = cloudApi.getJobStatus(jobId)
                val status = statusResponse.body() ?: continue

                when (status.status) {
                    "processing" -> {
                        _state.update {
                            it.copy(
                                reconstructionProgress = status.progress,
                                processingStatus = when {
                                    status.progress < 0.25f -> "Feature-Erkennung..."
                                    status.progress < 0.5f -> "Feature-Matching..."
                                    status.progress < 0.7f -> "3D-Rekonstruktion..."
                                    status.progress < 0.9f -> "Mesh-Erzeugung..."
                                    else -> "Finalisierung..."
                                }
                            )
                        }
                    }
                    "completed" -> {
                        completed = true
                        _state.update {
                            it.copy(processingStatus = "Mesh herunterladen...")
                        }
                        downloadAndSaveResult(jobId)
                    }
                    "failed" -> {
                        completed = true
                        _state.update {
                            it.copy(
                                isUploading = false,
                                error = status.error ?: "Rekonstruktion fehlgeschlagen"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                completed = true
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = "Verbindungsfehler: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun downloadAndSaveResult(jobId: String) {
        try {
            val downloadResponse = cloudApi.downloadResult(jobId)
            if (!downloadResponse.isSuccessful) {
                _state.update {
                    it.copy(isUploading = false, error = "Download fehlgeschlagen")
                }
                return
            }

            val scanId = UUID.randomUUID().toString()
            val meshFile = File(
                context.getExternalFilesDir(null),
                "photogrammetry_$scanId.ply"
            )

            downloadResponse.body()?.byteStream()?.use { input ->
                meshFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val project = ScanProject(
                id = scanId,
                name = "Foto-Scan ${_state.value.photoCount} Bilder",
                pointCount = 0,
                vertexCount = 0,
                triangleCount = 0,
                isCalibrated = true,
                scaleFactor = 1.0f,
                meshFilePath = meshFile.absolutePath
            )
            projectRepository.saveProject(project)

            cleanupPhotos()

            _state.update {
                it.copy(
                    isUploading = false,
                    completedScanId = scanId,
                    processingStatus = "Fertig!"
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isUploading = false,
                    error = "Speichern fehlgeschlagen: ${e.message}"
                )
            }
        }
    }

    private fun cleanupPhotos() {
        photoDir.deleteRecursively()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        if (_state.value.completedScanId == null) {
            cleanupPhotos()
        }
    }
}
