package me.anno.gpu.framebuffer

import me.anno.Build
import me.anno.cache.CacheSection
import me.anno.cache.ICacheData
import me.anno.gpu.GFX
import me.anno.gpu.deferred.BufferQuality
import me.anno.gpu.framebuffer.TargetType.Companion.FP16Targets
import me.anno.gpu.framebuffer.TargetType.Companion.FloatTargets
import me.anno.gpu.framebuffer.TargetType.Companion.UByteTargets
import me.anno.maths.Maths.clamp
import org.apache.logging.log4j.LogManager

object FBStack : CacheSection("FBStack") {

    private val LOGGER = LogManager.getLogger(FBStack::class)
    private const val timeout = 2100L

    private abstract class FBStackData(
        val width: Int,
        val height: Int,
        private val samples: Int,
        targetTypes: Array<TargetType>,
        depthBufferType: DepthBufferType
    ) : ICacheData {

        val readDepth = depthBufferType.read == true
        val writeDepth = depthBufferType.write

        var nextIndex = 0
        val data = ArrayList<IFramebuffer>()

        val targetTypes = if (readDepth && !GFX.supportsDepthTextures) {
            targetTypes + TargetType.FP16Target1
        } else targetTypes

        override fun destroy() {
            if (data.isNotEmpty()) {
                for (it in data) {
                    it.destroy()
                }
                printDestroyed(data.size)
                data.clear()
            }
        }

        fun getFrame(name: String): IFramebuffer {
            return if (nextIndex >= data.size) {
                if (targetTypes.size > GFX.maxSamples) {
                    val framebuffer = MultiFramebuffer(
                        name, width, height,
                        samples, targetTypes,
                        if (writeDepth) {
                            if (GFX.supportsDepthTextures) DepthBufferType.TEXTURE
                            else DepthBufferType.INTERNAL
                        } else DepthBufferType.NONE
                    )
                    data.add(framebuffer)
                    if (readDepth && !GFX.supportsDepthTextures) {
                        framebuffer.ensure() // ensure textures
                        // link depth texture to make things easier
                        framebuffer.depthTexture = framebuffer.targetsI.last().textures!!.last()
                    }
                    nextIndex = data.size
                    data.last()
                } else {
                    val framebuffer = Framebuffer(
                        name, width, height,
                        samples, targetTypes,
                        if (writeDepth) {
                            if (GFX.supportsDepthTextures) DepthBufferType.TEXTURE
                            else DepthBufferType.INTERNAL
                        } else DepthBufferType.NONE
                    )
                    data.add(framebuffer)
                    if (readDepth && !GFX.supportsDepthTextures) {
                        framebuffer.ensure() // ensure textures
                        // link depth texture to make things easier
                        framebuffer.depthTexture = framebuffer.textures!!.last()
                    }
                    nextIndex = data.size
                    data.last()
                }
            } else {
                val framebuffer = data[nextIndex++]
                if (Build.isDebug) when (framebuffer) {
                    is Framebuffer -> framebuffer.name = name
                    is MultiFramebuffer -> framebuffer.name = name
                }
                framebuffer
            }
        }

        abstract fun printDestroyed(size: Int)
    }

    private data class FBKey1(
        val width: Int,
        val height: Int,
        val channels: Int,
        val quality: BufferQuality,
        val samples: Int,
        val depthBufferType: DepthBufferType
    )

    private class FBStackData1(val key: FBKey1) :
        FBStackData(
            key.width, key.height, key.samples,
            arrayOf(getTargetType(key.channels, key.quality)),
            key.depthBufferType
        ) {
        override fun printDestroyed(size: Int) {
            val fs = if (size == 1) "1 framebuffer" else "$size framebuffers"
            LOGGER.info("Freed $fs of size ${key.width} x ${key.height}, samples: ${key.samples}, quality: ${key.quality}")
        }
    }

    private data class FBKey2(
        val width: Int, val height: Int, val targetType: TargetType,
        val samples: Int, val depthBufferType: DepthBufferType
    )

    private data class FBKey3(
        val width: Int, val height: Int, val targetTypes: Array<TargetType>,
        val samples: Int, val depthBufferType: DepthBufferType
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FBKey3

            if (width != other.width) return false
            if (height != other.height) return false
            if (!targetTypes.contentEquals(other.targetTypes)) return false
            if (samples != other.samples) return false
            if (depthBufferType != other.depthBufferType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + targetTypes.contentHashCode()
            result = 31 * result + samples
            result = 31 * result + depthBufferType.hashCode()
            return result
        }
    }

    private class FBStackData2(val key: FBKey2) :
        FBStackData(key.width, key.height, key.samples, arrayOf(key.targetType), key.depthBufferType) {
        override fun printDestroyed(size: Int) {
            val fs = if (size == 1) "1 framebuffer" else "$size framebuffers"
            LOGGER.info("Freed $fs of size ${key.width} x ${key.height}, samples: ${key.samples}, type: ${key.targetType}")
        }
    }

    private class FBStackData3(val key: FBKey3) :
        FBStackData(key.width, key.height, key.samples, key.targetTypes, key.depthBufferType) {
        override fun printDestroyed(size: Int) {
            val fs = if (size == 1) "1 framebuffer" else "$size framebuffers"
            LOGGER.info("Freed $fs of size ${key.width} x ${key.height}, samples: ${key.samples}, type: ${key.targetTypes}")
        }
    }

    private fun getValue(
        width: Int, height: Int, channels: Int,
        quality: BufferQuality, samples: Int,
        depthBufferType: DepthBufferType
    ): FBStackData {
        val key = FBKey1(width, height, channels, quality, clamp(samples, 1, GFX.maxSamples), depthBufferType)
        return getEntry(key, timeout, false) {
            FBStackData1(it)
        } as FBStackData
    }

    private fun getValue(
        width: Int, height: Int, targetType: TargetType, samples: Int,
        depthBufferType: DepthBufferType
    ): FBStackData {
        val key = FBKey2(width, height, targetType, clamp(samples, 1, GFX.maxSamples), depthBufferType)
        return getEntry(key, timeout, false) {
            FBStackData2(it)
        } as FBStackData
    }

    private fun getValue(
        w: Int, h: Int, targetTypes: Array<TargetType>, samples: Int,
        depthBufferType: DepthBufferType
    ): FBStackData {
        val key = FBKey3(w, h, targetTypes, clamp(samples, 1, GFX.maxSamples), depthBufferType)
        return getEntry(key, timeout, false) {
            FBStackData3(it)
        } as FBStackData
    }

    operator fun get(
        name: String, w: Int, h: Int, channels: Int,
        quality: BufferQuality, samples: Int,
        depthBufferType: DepthBufferType
    ): IFramebuffer {
        val value = getValue(w, h, channels, quality, samples, depthBufferType)
        synchronized(value) {
            return value.getFrame(name)
        }
    }

    operator fun get(
        name: String, w: Int, h: Int, channels: Int,
        fp: Boolean, samples: Int, depthBufferType: DepthBufferType
    ): IFramebuffer {
        val quality = if (fp) BufferQuality.HIGH_32 else BufferQuality.LOW_8
        return get(name, w, h, channels, quality, samples, depthBufferType)
    }

    operator fun get(
        name: String, w: Int, h: Int,
        targetType: TargetType, samples: Int,
        depthBufferType: DepthBufferType
    ): IFramebuffer {
        val value = getValue(w, h, targetType, samples, depthBufferType)
        synchronized(value) {
            return value.getFrame(name)
        }
    }

    operator fun get(
        name: String, w: Int, h: Int,
        targetTypes: Array<TargetType>, samples: Int,
        depthBufferType: DepthBufferType
    ): IFramebuffer {
        val value = getValue(w, h, targetTypes, samples, depthBufferType)
        synchronized(value) {
            return value.getFrame(name)
        }
    }

    fun getTargetType(channels: Int, quality: BufferQuality): TargetType {
        return when (quality) {
            BufferQuality.LOW_8 -> UByteTargets
            BufferQuality.MEDIUM_12, BufferQuality.HIGH_16 -> FP16Targets
            BufferQuality.HIGH_32 -> FloatTargets
        }[channels - 1]
    }

    fun reset(w: Int, h: Int) {
        synchronized(cache) {
            for (value in cache.values) {
                val data = value.data
                if (data is FBStackData && data.width == w && data.height == h) {
                    data.nextIndex = 0
                }
            }
        }
    }

    fun reset() {
        resetFBStack()
    }

    private fun resetFBStack() {
        synchronized(cache) {
            for (v in cache.values) {
                val data = v.data
                if (data is FBStackData) {
                    data.nextIndex = 0
                }
            }
        }
    }
}