package com.scanforge3d.scanning

import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.scanforge3d.data.model.PointCloud
import java.nio.ByteOrder
import javax.inject.Inject

class DepthFrameProcessor @Inject constructor() {

    companion object {
        const val MIN_CONFIDENCE: Int = 200
        const val MIN_DEPTH_M: Float = 0.1f
        const val MAX_DEPTH_M: Float = 3.0f
        const val MAX_POINTS_PER_FRAME: Int = 50_000
    }

    fun processFrame(frame: Frame): PointCloud? {
        try {
            val depthImage = frame.acquireRawDepthImage16Bits()
            val confidenceImage = frame.acquireRawDepthConfidenceImage()

            try {
                val camera = frame.camera
                if (camera.trackingState != TrackingState.TRACKING) {
                    return null
                }

                val intrinsics = camera.textureIntrinsics
                val fx = intrinsics.focalLength[0]
                val fy = intrinsics.focalLength[1]
                val cx = intrinsics.principalPoint[0]
                val cy = intrinsics.principalPoint[1]

                val width = depthImage.width
                val height = depthImage.height

                val depthBuffer = depthImage.planes[0].buffer
                    .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val confidenceBuffer = confidenceImage.planes[0].buffer

                val cameraPose = camera.displayOrientedPose
                val viewMatrix = FloatArray(16)
                cameraPose.toMatrix(viewMatrix, 0)

                val points = mutableListOf<FloatArray>()
                val stride = maxOf(1, (width * height) / MAX_POINTS_PER_FRAME)

                for (y in 0 until height step stride) {
                    for (x in 0 until width step stride) {
                        val idx = y * width + x

                        val confidence = confidenceBuffer.get(idx).toInt() and 0xFF
                        if (confidence < MIN_CONFIDENCE) continue

                        val depthMm = depthBuffer.get(idx).toInt() and 0xFFFF
                        val depthM = depthMm / 1000.0f

                        if (depthM < MIN_DEPTH_M || depthM > MAX_DEPTH_M) continue

                        val localX = (x - cx) / fx * depthM
                        val localY = (y - cy) / fy * depthM
                        val localZ = -depthM

                        val worldPoint = transformPoint(viewMatrix, localX, localY, localZ)
                        points.add(worldPoint)
                    }
                }

                return PointCloud(
                    points = points.toTypedArray(),
                    timestamp = frame.timestamp,
                    cameraPose = cameraPose,
                    pointCount = points.size
                )

            } finally {
                depthImage.close()
                confidenceImage.close()
            }
        } catch (e: NotYetAvailableException) {
            return null
        }
    }

    private fun transformPoint(
        matrix: FloatArray, x: Float, y: Float, z: Float
    ): FloatArray {
        return floatArrayOf(
            matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
            matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
            matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]
        )
    }
}
