package com.scanforge3d.processing

import com.scanforge3d.data.model.TriangleMesh
import javax.inject.Inject

class MeshOptimizer @Inject constructor(
    private val native: NativeMeshProcessor
) {
    fun decimate(mesh: TriangleMesh, targetRatio: Float = 0.5f): TriangleMesh {
        val result = native.decimateMesh(mesh.serializedData, targetRatio)
        return TriangleMesh.fromSerializedData(result)
    }

    fun repair(mesh: TriangleMesh): TriangleMesh {
        val result = native.repairMesh(mesh.serializedData)
        return TriangleMesh.fromSerializedData(result)
    }
}
