package com.scanforge3d.processing

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeMeshProcessor @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("scanforge_native")
        }
    }

    // Point cloud processing
    external fun voxelGridFilter(pointsFlat: FloatArray, voxelSize: Float): FloatArray
    external fun statisticalOutlierRemoval(
        pointsFlat: FloatArray, kNeighbors: Int, stdRatio: Float
    ): FloatArray
    external fun icpRegistration(
        sourceFlat: FloatArray, targetFlat: FloatArray,
        maxIterations: Int, tolerance: Float
    ): FloatArray

    // Normal estimation
    external fun estimateNormals(pointsFlat: FloatArray, kNeighbors: Int): FloatArray

    // Mesh reconstruction
    external fun poissonReconstruction(
        pointsWithNormals: FloatArray, depth: Int
    ): FloatArray

    // Mesh post-processing
    external fun decimateMesh(meshData: FloatArray, targetRatio: Float): FloatArray
    external fun repairMesh(meshData: FloatArray): FloatArray

    // Export
    external fun exportSTL(meshData: FloatArray, filePath: String): Boolean
    external fun exportOBJ(meshData: FloatArray, filePath: String): Boolean
    external fun exportPLY(meshData: FloatArray, filePath: String): Boolean
}
