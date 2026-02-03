package com.scanforge3d.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scanDir: File
        get() = File(context.filesDir, "scans").also { it.mkdirs() }

    fun saveMeshData(scanId: String, meshData: FloatArray): File {
        val file = File(scanDir, "${scanId}.mesh")
        FileOutputStream(file).use { fos ->
            ObjectOutputStream(fos).use { oos ->
                oos.writeObject(meshData)
            }
        }
        return file
    }

    fun loadMeshData(scanId: String): FloatArray? {
        val file = File(scanDir, "${scanId}.mesh")
        if (!file.exists()) return null
        return try {
            file.inputStream().use { fis ->
                ObjectInputStream(fis).use { ois ->
                    ois.readObject() as FloatArray
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun savePointCloud(scanId: String, pointsFlat: FloatArray): File {
        val file = File(scanDir, "${scanId}.points")
        FileOutputStream(file).use { fos ->
            ObjectOutputStream(fos).use { oos ->
                oos.writeObject(pointsFlat)
            }
        }
        return file
    }

    fun loadPointCloud(scanId: String): FloatArray? {
        val file = File(scanDir, "${scanId}.points")
        if (!file.exists()) return null
        return try {
            file.inputStream().use { fis ->
                ObjectInputStream(fis).use { ois ->
                    ois.readObject() as FloatArray
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteScanData(scanId: String) {
        File(scanDir, "${scanId}.mesh").delete()
        File(scanDir, "${scanId}.points").delete()
    }
}
