package com.scanforge3d.ui.preview

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Converts serialized mesh data into Filament vertex/index buffers
 * and computes bounding box for camera positioning.
 *
 * Mesh format: [vertex_count, triangle_count, v0x,v0y,v0z, ..., t0a,t0b,t0c, ...]
 */
class MeshRenderer(private val context: Context) {

    data class BoundingBox(
        val minX: Float, val minY: Float, val minZ: Float,
        val maxX: Float, val maxY: Float, val maxZ: Float
    ) {
        val centerX get() = (minX + maxX) / 2f
        val centerY get() = (minY + maxY) / 2f
        val centerZ get() = (minZ + maxZ) / 2f
        val diagonal get() = sqrt(
            (maxX - minX) * (maxX - minX) +
            (maxY - minY) * (maxY - minY) +
            (maxZ - minZ) * (maxZ - minZ)
        )
    }

    fun computeBoundingBox(meshData: FloatArray): BoundingBox {
        val vertexCount = meshData[0].toInt()
        if (vertexCount == 0) return BoundingBox(0f, 0f, 0f, 0f, 0f, 0f)

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        for (i in 0 until vertexCount) {
            val offset = 2 + i * 3
            val x = meshData[offset]; val y = meshData[offset + 1]; val z = meshData[offset + 2]
            minX = min(minX, x); minY = min(minY, y); minZ = min(minZ, z)
            maxX = max(maxX, x); maxY = max(maxY, y); maxZ = max(maxZ, z)
        }
        return BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
    }

    /**
     * Creates vertex buffer with positions and computed per-face normals.
     * Uses 2 buffer slots: slot 0 = positions, slot 1 = normals.
     */
    fun createVertexBuffer(engine: Engine, meshData: FloatArray): VertexBuffer {
        val vertexCount = meshData[0].toInt()
        val vertexOffset = 2

        val positionBuffer = ByteBuffer.allocate(vertexCount * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val normalBuffer = ByteBuffer.allocate(vertexCount * 3 * 4)
            .order(ByteOrder.nativeOrder())

        // Accumulate face normals per vertex
        val normals = FloatArray(vertexCount * 3)
        val triangleCount = meshData[1].toInt()
        val triOffset = vertexOffset + vertexCount * 3

        for (t in 0 until triangleCount) {
            val ti = triOffset + t * 3
            val a = meshData[ti].toInt()
            val b = meshData[ti + 1].toInt()
            val c = meshData[ti + 2].toInt()

            val ax = meshData[vertexOffset + a * 3]; val ay = meshData[vertexOffset + a * 3 + 1]; val az = meshData[vertexOffset + a * 3 + 2]
            val bx = meshData[vertexOffset + b * 3]; val by = meshData[vertexOffset + b * 3 + 1]; val bz = meshData[vertexOffset + b * 3 + 2]
            val cx = meshData[vertexOffset + c * 3]; val cy = meshData[vertexOffset + c * 3 + 1]; val cz = meshData[vertexOffset + c * 3 + 2]

            val e1x = bx - ax; val e1y = by - ay; val e1z = bz - az
            val e2x = cx - ax; val e2y = cy - ay; val e2z = cz - az
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x

            for (idx in intArrayOf(a, b, c)) {
                normals[idx * 3] += nx
                normals[idx * 3 + 1] += ny
                normals[idx * 3 + 2] += nz
            }
        }

        // Normalize and fill buffers
        for (i in 0 until vertexCount) {
            val offset = vertexOffset + i * 3
            positionBuffer.putFloat(meshData[offset])
            positionBuffer.putFloat(meshData[offset + 1])
            positionBuffer.putFloat(meshData[offset + 2])

            val nx = normals[i * 3]; val ny = normals[i * 3 + 1]; val nz = normals[i * 3 + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-8f) {
                normalBuffer.putFloat(nx / len)
                normalBuffer.putFloat(ny / len)
                normalBuffer.putFloat(nz / len)
            } else {
                normalBuffer.putFloat(0f)
                normalBuffer.putFloat(1f)
                normalBuffer.putFloat(0f)
            }
        }
        positionBuffer.flip()
        normalBuffer.flip()

        val vb = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(2)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)

        vb.setBufferAt(engine, 0, positionBuffer)
        vb.setBufferAt(engine, 1, normalBuffer)
        return vb
    }

    fun createIndexBuffer(engine: Engine, meshData: FloatArray): IndexBuffer {
        val vertexCount = meshData[0].toInt()
        val triangleCount = meshData[1].toInt()
        val triOffset = 2 + vertexCount * 3
        val indexCount = triangleCount * 3

        val buffer = ByteBuffer.allocate(indexCount * 4)
            .order(ByteOrder.nativeOrder())

        for (t in 0 until triangleCount) {
            val idx = triOffset + t * 3
            buffer.putInt(meshData[idx].toInt())
            buffer.putInt(meshData[idx + 1].toInt())
            buffer.putInt(meshData[idx + 2].toInt())
        }
        buffer.flip()

        return IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
            .also { it.setBuffer(engine, buffer) }
    }
}
