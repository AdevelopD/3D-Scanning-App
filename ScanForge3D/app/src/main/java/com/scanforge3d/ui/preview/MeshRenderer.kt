package com.scanforge3d.ui.preview

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MeshRenderer(private val context: Context) {

    fun createVertexBuffer(engine: Engine, meshData: FloatArray): VertexBuffer {
        val vertexCount = meshData[0].toInt()
        val vertexOffset = 2

        val positionBuffer = ByteBuffer.allocate(vertexCount * 3 * 4)
            .order(ByteOrder.nativeOrder())

        for (i in 0 until vertexCount) {
            val idx = vertexOffset + i * 3
            positionBuffer.putFloat(meshData[idx])
            positionBuffer.putFloat(meshData[idx + 1])
            positionBuffer.putFloat(meshData[idx + 2])
        }
        positionBuffer.flip()

        return VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                0,
                VertexBuffer.AttributeType.FLOAT3,
                0, 12
            )
            .build(engine)
            .also { it.setBufferAt(engine, 0, positionBuffer) }
    }

    fun createIndexBuffer(engine: Engine, meshData: FloatArray): IndexBuffer {
        val vertexCount = meshData[0].toInt()
        val triangleCount = meshData[1].toInt()
        val triOffset = 2 + vertexCount * 3
        val indexCount = triangleCount * 3

        val indexBuffer = ByteBuffer.allocate(indexCount * 4)
            .order(ByteOrder.nativeOrder())

        for (t in 0 until triangleCount) {
            val idx = triOffset + t * 3
            indexBuffer.putInt(meshData[idx].toInt())
            indexBuffer.putInt(meshData[idx + 1].toInt())
            indexBuffer.putInt(meshData[idx + 2].toInt())
        }
        indexBuffer.flip()

        return IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
            .also { it.setBuffer(engine, indexBuffer) }
    }
}
