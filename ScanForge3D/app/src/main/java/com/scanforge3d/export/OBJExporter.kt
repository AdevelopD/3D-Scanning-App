package com.scanforge3d.export

import android.content.Context
import android.net.Uri
import com.scanforge3d.processing.NativeMeshProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class OBJExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val native: NativeMeshProcessor
) {
    suspend fun exportToFile(meshData: FloatArray, file: File): Boolean {
        return native.exportOBJ(meshData, file.absolutePath)
    }

    suspend fun exportToUri(meshData: FloatArray, uri: Uri): Boolean {
        return try {
            val tempFile = File(context.cacheDir, "temp_export.obj")
            val success = native.exportOBJ(meshData, tempFile.absolutePath)
            if (success) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    tempFile.inputStream().use { input -> input.copyTo(output) }
                }
                tempFile.delete()
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
