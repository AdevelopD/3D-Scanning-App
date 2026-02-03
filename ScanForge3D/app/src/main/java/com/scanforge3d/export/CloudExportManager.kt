package com.scanforge3d.export

import android.content.Context
import com.scanforge3d.data.remote.CloudApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class CloudExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: CloudApiService
) {
    sealed class ExportState {
        object Uploading : ExportState()
        data class Processing(val progress: Float) : ExportState()
        data class Completed(val filePath: String) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    fun exportAsSTEP(stlFile: File): Flow<ExportState> = flow {
        emit(ExportState.Uploading)

        val requestBody = stlFile.asRequestBody("application/sla".toMediaType())
        val part = MultipartBody.Part.createFormData("file", stlFile.name, requestBody)

        val uploadResponse = api.uploadMesh(part)
        if (!uploadResponse.isSuccessful) {
            emit(ExportState.Error("Upload fehlgeschlagen: ${uploadResponse.code()}"))
            return@flow
        }

        val jobId = uploadResponse.body()?.job_id
            ?: run {
                emit(ExportState.Error("Keine Job-ID erhalten"))
                return@flow
            }

        var completed = false
        while (!completed) {
            delay(2000)

            val statusResponse = api.getJobStatus(jobId)
            val status = statusResponse.body()

            when (status?.status) {
                "processing" -> {
                    emit(ExportState.Processing(status.progress))
                }
                "completed" -> {
                    completed = true
                    val downloadResponse = api.downloadResult(jobId)
                    if (downloadResponse.isSuccessful) {
                        val outputFile = File(
                            context.getExternalFilesDir(null),
                            "scan_${System.currentTimeMillis()}.step"
                        )
                        downloadResponse.body()?.byteStream()?.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        emit(ExportState.Completed(outputFile.absolutePath))
                    } else {
                        emit(ExportState.Error("Download fehlgeschlagen"))
                    }
                }
                "failed" -> {
                    completed = true
                    emit(ExportState.Error(status.error ?: "Unbekannter Fehler"))
                }
            }
        }
    }
}
