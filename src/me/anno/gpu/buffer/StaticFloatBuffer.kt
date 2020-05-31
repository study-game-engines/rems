package me.anno.gpu.buffer

import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.ByteOrder

open class StaticFloatBuffer(attributes: List<Attribute>, val floatCount: Int): GPUFloatBuffer(attributes){

    init {
        createNioBuffer()
    }

    fun put(v: Vector3f){
        put(v.x, v.y, v.z)
    }

    fun put(x: Float, y: Float, z: Float){
        put(x)
        put(y)
        put(z)
    }

    fun put(x: Float, y: Float){
        put(x)
        put(y)
    }

    fun put(f: Float){
        floatBuffer.put(f)
        isUpToDate = false
    }

    final override fun createNioBuffer() {
        val nio = ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder())
        nioBuffer = nio
        floatBuffer = nio.asFloatBuffer()
        floatBuffer.position(0)
    }
}