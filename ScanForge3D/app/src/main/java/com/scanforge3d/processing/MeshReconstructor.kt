package com.scanforge3d.processing

import com.scanforge3d.data.model.TriangleMesh
import javax.inject.Inject

class MeshReconstructor @Inject constructor(
    private val native: NativeMeshProcessor
) {
    companion object {
        private const val DEFAULT_NORMAL_K_NEIGHBORS = 15
    }

    fun reconstruct(
        pointsFlat: FloatArray,
        poissonDepth: Int = 9,
        normalKNeighbors: Int = DEFAULT_NORMAL_K_NEIGHBORS
    ): TriangleMesh {
        val pointsWithNormals = native.estimateNormals(pointsFlat, normalKNeighbors)
        val meshData = native.poissonReconstruction(pointsWithNormals, poissonDepth)
        return TriangleMesh.fromSerializedData(meshData)
    }
}
