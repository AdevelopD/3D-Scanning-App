package com.scanforge3d.ui.calibration

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class CalibrationViewModel @Inject constructor() : ViewModel() {

    data class CalibrationState(
        val point1: FloatArray? = null,
        val point2: FloatArray? = null,
        val measuredDistance: Float = 0f,
        val knownDistance: Float = 0f,
        val scaleFactor: Float = 1.0f,
        val isCalibrated: Boolean = false
    )

    private val _state = MutableStateFlow(CalibrationState())
    val state: StateFlow<CalibrationState> = _state

    fun setPoint1(x: Float, y: Float, z: Float) {
        _state.value = _state.value.copy(point1 = floatArrayOf(x, y, z))
        recalculate()
    }

    fun setPoint2(x: Float, y: Float, z: Float) {
        _state.value = _state.value.copy(point2 = floatArrayOf(x, y, z))
        recalculate()
    }

    fun setKnownDistance(distanceMm: Float) {
        _state.value = _state.value.copy(knownDistance = distanceMm)
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val p1 = s.point1 ?: return
        val p2 = s.point2 ?: return

        val dx = p2[0] - p1[0]
        val dy = p2[1] - p1[1]
        val dz = p2[2] - p1[2]
        val measured = sqrt(dx * dx + dy * dy + dz * dz) * 1000f

        val scale = if (measured > 0.001f && s.knownDistance > 0f) {
            s.knownDistance / measured
        } else 1.0f

        _state.value = s.copy(
            measuredDistance = measured,
            scaleFactor = scale,
            isCalibrated = s.knownDistance > 0f && measured > 0.001f
        )
    }

    fun reset() {
        _state.value = CalibrationState()
    }
}
