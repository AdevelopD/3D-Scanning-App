package com.scanforge3d.processing

import com.scanforge3d.data.model.TriangleMesh
import javax.inject.Inject

class MeshReconstructor @Inject constructor(
    private val native: NativeMeshProcessor
) {
    fun reconstruct(
        pointsFlat: FloatArray,
        poissonDepth: Int = 9
    ): TriangleMesh {
        val pointsWithNormals = estimateNormals(pointsFlat)
        val meshData = native.poissonReconstruction(pointsWithNormals, poissonDepth)
        return TriangleMesh.fromSerializedData(meshData)
    }

    private fun estimateNormals(pointsFlat: FloatArray): FloatArray {
        val numPoints = pointsFlat.size / 3
        val result = FloatArray(numPoints * 6)

        for (i in 0 until numPoints) {
            val idx = i * 3
            result[i * 6] = pointsFlat[idx]
            result[i * 6 + 1] = pointsFlat[idx + 1]
            result[i * 6 + 2] = pointsFlat[idx + 2]
            result[i * 6 + 3] = 0f
            result[i * 6 + 4] = 1f
            result[i * 6 + 5] = 0f
        }
        return result
    }
}
