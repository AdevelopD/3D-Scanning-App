package com.scanforge3d.scanning

import com.google.ar.core.Frame
import com.scanforge3d.data.model.PointCloud
import com.scanforge3d.data.model.ScanMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject

class ScanSessionController @Inject constructor(
    private val depthFrameProcessor: DepthFrameProcessor,
    private val pointCloudAccumulator: PointCloudAccumulator
) {
    data class ScanState(
        val scanId: String = "",
        val isScanning: Boolean = false,
        val pointCount: Int = 0,
        val frameCount: Int = 0,
        val surfaceCoverage: Float = 0f,
        val qualityScore: Float = 0f,
        val qualityLabel: String = "---"
    )

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    private var startTimeMs: Long = 0L
    private var totalConfidence: Float = 0f
    private var confidenceFrames: Int = 0

    fun startScan() {
        pointCloudAccumulator.reset()
        startTimeMs = System.currentTimeMillis()
        totalConfidence = 0f
        confidenceFrames = 0

        _state.value = ScanState(
            scanId = UUID.randomUUID().toString(),
            isScanning = true
        )
    }

    fun processFrame(frame: Frame) {
        if (!_state.value.isScanning) return

        val pointCloud = depthFrameProcessor.processFrame(frame) ?: return
        pointCloudAccumulator.addFrame(pointCloud)

        val pointCount = pointCloudAccumulator.getPointCount()
        val frameCount = pointCloudAccumulator.getFrameCount()
        val quality = estimateQuality(pointCount, frameCount)

        _state.value = _state.value.copy(
            pointCount = pointCount,
            frameCount = frameCount,
            surfaceCoverage = estimateCoverage(pointCount),
            qualityScore = quality,
            qualityLabel = qualityToLabel(quality)
        )
    }

    fun stopScan(): Array<FloatArray> {
        _state.value = _state.value.copy(isScanning = false)
        return pointCloudAccumulator.getAccumulatedCloud()
    }

    fun getScanId(): String = _state.value.scanId

    fun getMetadata(): ScanMetadata {
        return ScanMetadata(
            deviceModel = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            arcoreVersion = "1.41.0",
            hasToFSensor = false,
            depthResolution = Pair(640, 360),
            totalFrames = pointCloudAccumulator.getFrameCount(),
            scanDurationMs = System.currentTimeMillis() - startTimeMs,
            averageConfidence = if (confidenceFrames > 0) totalConfidence / confidenceFrames else 0f
        )
    }

    private fun estimateQuality(pointCount: Int, frameCount: Int): Float {
        if (frameCount == 0) return 0f
        val densityScore = (pointCount.toFloat() / 500_000f).coerceIn(0f, 1f)
        val frameScore = (frameCount.toFloat() / 100f).coerceIn(0f, 1f)
        return (densityScore * 0.7f + frameScore * 0.3f)
    }

    private fun estimateCoverage(pointCount: Int): Float {
        return (pointCount.toFloat() / 1_000_000f).coerceIn(0f, 1f)
    }

    private fun qualityToLabel(quality: Float): String {
        return when {
            quality < 0.2f -> "Niedrig"
            quality < 0.5f -> "Mittel"
            quality < 0.8f -> "Gut"
            else -> "Sehr gut"
        }
    }
}
