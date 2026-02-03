package com.scanforge3d.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ScanProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ScanSession(
    @PrimaryKey val id: String,
    val projectId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val frameCount: Int = 0,
    val pointCount: Int = 0,
    val durationMs: Long = 0,
    val pointCloudPath: String? = null,
    val status: String = "pending" // pending, processing, completed, failed
)
