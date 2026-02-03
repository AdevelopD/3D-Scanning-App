package com.scanforge3d.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MeshProcessingPipeline @Inject constructor(
    private val native: NativeMeshProcessor
) {
    enum class ReconstructionMethod {
        POISSON,         // SDF via depth-controlled voxel grid
        MARCHING_CUBES   // Direct voxel size control
    }

    data class PipelineConfig(
        val voxelSize: Float = 0.002f,
        val sorKNeighbors: Int = 20,
        val sorStdRatio: Float = 2.0f,
        val normalKNeighbors: Int = 15,
        val reconstructionMethod: ReconstructionMethod = ReconstructionMethod.POISSON,
        val poissonDepth: Int = 9,
        val marchingCubesVoxelSize: Float = 0.003f,
        val decimationRatio: Float = 0.5f,
        val smoothingIterations: Int = 3,
        val smoothingLambda: Float = 0.5f,
        val scaleFactor: Float = 1.0f
    )

    data class PipelineResult(
        val meshData: FloatArray,
        val vertexCount: Int,
        val triangleCount: Int,
        val isWatertight: Boolean,
        val processingTimeMs: Long
    )

    interface ProgressCallback {
        fun onProgress(step: String, progress: Float)
    }

    suspend fun process(
        pointsFlat: FloatArray,
        config: PipelineConfig = PipelineConfig(),
        callback: ProgressCallback? = null
    ): PipelineResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        callback?.onProgress("Downsampling...", 0.05f)
        val downsampled = native.voxelGridFilter(pointsFlat, config.voxelSize)

        callback?.onProgress("Rauschen entfernen...", 0.15f)
        val cleaned = native.statisticalOutlierRemoval(
            downsampled, config.sorKNeighbors, config.sorStdRatio
        )

        callback?.onProgress("Normalen berechnen...", 0.25f)
        val pointsWithNormals = native.estimateNormals(cleaned, config.normalKNeighbors)

        callback?.onProgress("Oberfläche rekonstruieren...", 0.45f)
        val rawMesh = when (config.reconstructionMethod) {
            ReconstructionMethod.POISSON ->
                native.poissonReconstruction(pointsWithNormals, config.poissonDepth)
            ReconstructionMethod.MARCHING_CUBES ->
                native.marchingCubesReconstruction(pointsWithNormals, config.marchingCubesVoxelSize)
        }

        callback?.onProgress("Mesh reparieren...", 0.60f)
        val repairedMesh = native.repairMesh(rawMesh)

        callback?.onProgress("Glätten...", 0.72f)
        val smoothedMesh = if (config.smoothingIterations > 0) {
            native.smoothMesh(repairedMesh, config.smoothingIterations, config.smoothingLambda)
        } else {
            repairedMesh
        }

        callback?.onProgress("Optimieren...", 0.85f)
        val decimatedMesh = native.decimateMesh(smoothedMesh, config.decimationRatio)

        val finalMesh = if (config.scaleFactor != 1.0f) {
            applyScale(decimatedMesh, config.scaleFactor)
        } else {
            decimatedMesh
        }

        callback?.onProgress("Fertig!", 1.0f)

        val vertexCount = finalMesh[0].toInt()
        val triangleCount = finalMesh[1].toInt()

        PipelineResult(
            meshData = finalMesh,
            vertexCount = vertexCount,
            triangleCount = triangleCount,
            isWatertight = true,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun applyScale(meshData: FloatArray, scale: Float): FloatArray {
        val result = meshData.copyOf()
        val vcount = result[0].toInt()
        for (i in 0 until vcount) {
            val offset = 2 + i * 3
            result[offset] *= scale
            result[offset + 1] *= scale
            result[offset + 2] *= scale
        }
        return result
    }
}
