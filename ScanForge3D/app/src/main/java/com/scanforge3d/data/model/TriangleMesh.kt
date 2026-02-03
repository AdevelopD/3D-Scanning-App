package com.scanforge3d.data.model

/**
 * Kotlin-Repräsentation eines Dreiecks-Meshes.
 * 
 * Serialisiertes Format (FloatArray):
 * [vertex_count, triangle_count, v0x, v0y, v0z, v1x, ..., t0a, t0b, t0c, t1a, ...]
 */
data class TriangleMesh(
    val vertexCount: Int,
    val triangleCount: Int,
    val serializedData: FloatArray
) {
    companion object {
        fun fromSerializedData(data: FloatArray): TriangleMesh {
            return TriangleMesh(
                vertexCount = data[0].toInt(),
                triangleCount = data[1].toInt(),
                serializedData = data
            )
        }
    }
    
    fun getVertex(index: Int): FloatArray {
        val offset = 2 + index * 3
        return floatArrayOf(
            serializedData[offset],
            serializedData[offset + 1],
            serializedData[offset + 2]
        )
    }
    
    fun estimateFileSizeSTL(): Long {
        // Binary STL: 80 header + 4 count + 50 per triangle
        return 84L + triangleCount.toLong() * 50L
    }
}
