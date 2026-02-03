package com.scanforge3d.scanning

import javax.inject.Inject

class ConfidenceFilter @Inject constructor() {

    companion object {
        const val DEFAULT_THRESHOLD: Int = 200
    }

    var threshold: Int = DEFAULT_THRESHOLD

    fun filterPoints(
        points: Array<FloatArray>,
        confidences: IntArray
    ): Array<FloatArray> {
        return points.filterIndexed { index, _ ->
            confidences[index] >= threshold
        }.toTypedArray()
    }
}
