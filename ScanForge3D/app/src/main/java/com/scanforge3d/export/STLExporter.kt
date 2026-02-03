package com.scanforge3d.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.scanforge3d.processing.NativeMeshProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

class STLExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val native: NativeMeshProcessor
) {
    suspend fun exportToDownloads(
        meshData: FloatArray,
        fileName: String = "scan_${System.currentTimeMillis()}.stl"
    ): Uri? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloadsDir, fileName)
        val success = native.exportSTL(meshData, file.absolutePath)
        return if (success) Uri.fromFile(file) else null
    }

    suspend fun exportToUri(meshData: FloatArray, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                writeSTLToStream(meshData, outputStream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeSTLToStream(meshData: FloatArray, stream: OutputStream) {
        val vertexCount = meshData[0].toInt()
        val triangleCount = meshData[1].toInt()
        val buffered = BufferedOutputStream(stream, 65536)

        val header = ByteArray(80)
        "ScanForge3D".toByteArray().copyInto(header)
        buffered.write(header)

        val countBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        countBuf.putInt(triangleCount)
        buffered.write(countBuf.array())

        val vertexOffset = 2
        val triangleOffset = vertexOffset + vertexCount * 3
        val triBuf = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)

        for (t in 0 until triangleCount) {
            val triIdx = triangleOffset + t * 3
            val a = meshData[triIdx].toInt()
            val b = meshData[triIdx + 1].toInt()
            val c = meshData[triIdx + 2].toInt()

            val v0x = meshData[vertexOffset + a * 3]
            val v0y = meshData[vertexOffset + a * 3 + 1]
            val v0z = meshData[vertexOffset + a * 3 + 2]
            val v1x = meshData[vertexOffset + b * 3]
            val v1y = meshData[vertexOffset + b * 3 + 1]
            val v1z = meshData[vertexOffset + b * 3 + 2]
            val v2x = meshData[vertexOffset + c * 3]
            val v2y = meshData[vertexOffset + c * 3 + 1]
            val v2z = meshData[vertexOffset + c * 3 + 2]

            val e1x = v1x - v0x; val e1y = v1y - v0y; val e1z = v1z - v0z
            val e2x = v2x - v0x; val e2y = v2y - v0y; val e2z = v2z - v0z
            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val len = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
            if (len > 1e-8f) { nx /= len; ny /= len; nz /= len }

            triBuf.clear()
            triBuf.putFloat(nx); triBuf.putFloat(ny); triBuf.putFloat(nz)
            triBuf.putFloat(v0x); triBuf.putFloat(v0y); triBuf.putFloat(v0z)
            triBuf.putFloat(v1x); triBuf.putFloat(v1y); triBuf.putFloat(v1z)
            triBuf.putFloat(v2x); triBuf.putFloat(v2y); triBuf.putFloat(v2z)
            triBuf.putShort(0)
            buffered.write(triBuf.array())
        }

        buffered.flush()
    }
}
