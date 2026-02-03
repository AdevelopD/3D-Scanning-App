package com.scanforge3d.scanning

import com.scanforge3d.data.model.PointCloud
import com.scanforge3d.processing.NativeMeshProcessor
import javax.inject.Inject

class PointCloudAccumulator @Inject constructor(
    private val nativeMeshProcessor: NativeMeshProcessor
) {
    companion object {
        const val VOXEL_SIZE_M: Float = 0.002f
        const val MAX_ACCUMULATED_POINTS: Int = 2_000_000
    }

    private val accumulatedPoints = mutableListOf<FloatArray>()
    private var frameCount = 0

    fun addFrame(pointCloud: PointCloud) {
        accumulatedPoints.addAll(pointCloud.points)
        frameCount++

        if (frameCount % 10 == 0) {
            downsample()
        }
    }

    private fun downsample() {
        if (accumulatedPoints.size < 1000) return

        val flatPoints = flattenPoints(accumulatedPoints)
        val downsampled = nativeMeshProcessor.voxelGridFilter(flatPoints, VOXEL_SIZE_M)
        accumulatedPoints.clear()
        accumulatedPoints.addAll(unflattenPoints(downsampled))
    }

    fun getAccumulatedCloud(): Array<FloatArray> {
        downsample()
        return accumulatedPoints.toTypedArray()
    }

    fun getPointCount(): Int = accumulatedPoints.size
    fun getFrameCount(): Int = frameCount

    fun reset() {
        accumulatedPoints.clear()
        frameCount = 0
    }

    private fun flattenPoints(points: List<FloatArray>): FloatArray {
        val flat = FloatArray(points.size * 3)
        points.forEachIndexed { i, p ->
            flat[i * 3] = p[0]
            flat[i * 3 + 1] = p[1]
            flat[i * 3 + 2] = p[2]
        }
        return flat
    }

    private fun unflattenPoints(flat: FloatArray): List<FloatArray> {
        val points = mutableListOf<FloatArray>()
        for (i in flat.indices step 3) {
            points.add(floatArrayOf(flat[i], flat[i + 1], flat[i + 2]))
        }
        return points
    }
}
