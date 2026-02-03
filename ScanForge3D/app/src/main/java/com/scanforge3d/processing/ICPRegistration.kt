package com.scanforge3d.processing

import javax.inject.Inject

class ICPRegistration @Inject constructor(
    private val native: NativeMeshProcessor
) {
    fun align(
        sourceFlatPoints: FloatArray,
        targetFlatPoints: FloatArray,
        maxIterations: Int = 50,
        tolerance: Float = 1e-6f
    ): FloatArray {
        return native.icpRegistration(sourceFlatPoints, targetFlatPoints, maxIterations, tolerance)
    }
}
