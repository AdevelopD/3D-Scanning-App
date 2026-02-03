package com.scanforge3d.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_projects")
data class ScanProject(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pointCount: Int = 0,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0,
    val isCalibrated: Boolean = false,
    val scaleFactor: Float = 1.0f,
    val meshFilePath: String? = null,
    val thumbnailPath: String? = null,
    val notes: String = ""
)
