package com.scanforge3d.data.model

import com.google.ar.core.Pose

data class PointCloud(
    val points: Array<FloatArray>,  // Jeder Eintrag: [x, y, z]
    val timestamp: Long,
    val cameraPose: Pose,
    val pointCount: Int
) {
    fun toFlatArray(): FloatArray {
        val flat = FloatArray(points.size * 3)
        points.forEachIndexed { i, p ->
            flat[i * 3] = p[0]
            flat[i * 3 + 1] = p[1]
            flat[i * 3 + 2] = p[2]
        }
        return flat
    }
}
