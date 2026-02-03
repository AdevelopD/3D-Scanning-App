package com.scanforge3d.data.model

data class ScanMetadata(
    val deviceModel: String,
    val androidVersion: String,
    val arcoreVersion: String,
    val hasToFSensor: Boolean,
    val depthResolution: Pair<Int, Int>,  // width x height
    val totalFrames: Int,
    val scanDurationMs: Long,
    val averageConfidence: Float
)
