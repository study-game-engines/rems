package me.anno.gpu

import me.anno.cache.instances.VideoCache
import me.anno.fonts.FontManager.TextCache
import me.anno.gpu.GFX.supportsClipControl
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.buffer.OpenGLBuffer
import me.anno.gpu.framebuffer.*
import me.anno.gpu.shader.OpenGLShader
import me.anno.gpu.shader.Renderer
import me.anno.gpu.shader.Renderer.Companion.colorRenderer
import me.anno.gpu.texture.Texture2D
import me.anno.image.ImageGPUCache
import me.anno.utils.structures.stacks.SecureStack
import org.lwjgl.opengl.GL20C.GL_LOWER_LEFT
import org.lwjgl.opengl.GL45C.*

/**
 * holds rendering-related state,
 * currently, with OpenGL, this must be changed from a single thread only!
 * all functions feature rendering-callbacks, so you can change settings without having to worry about the previously set state by your caller
 *
 * renamed from OpenGL to GFXState, because we might support Vulkan in the future
 * */
object GFXState {

    var session = 0
        private set

    /**
     * in OpenGL ES (e.g. on Android),
     * the context can be destroyed, when the app
     * is closed temporarily to save resources & energy
     * then all loaded memory has become invalid
     * */
    fun newSession() {
        session++
        GFX.gpuTasks.clear() // they all have become invalid
        OpenGLShader.invalidateBinding()
        Texture2D.invalidateBinding()
        OpenGLBuffer.invalidateBinding()
        // clear all caches, which contain gpu data
        FBStack.clear()
        TextCache.clear()
        VideoCache.clear()
        ImageGPUCache.clear()
    }

    // the renderer is set per framebuffer; makes the most sense
    // additionally, it would be possible to set the blend-mode and depth there in a game setting
    // (not that practical in RemsStudio)
    // val renderer = SecureStack(Renderer.colorRenderer)

    val blendMode = object : SecureStack<Any?>(BlendMode.DEFAULT) {
        // could be optimized
        override fun onChangeValue(newValue: Any?, oldValue: Any?) {
            // LOGGER.info("Blending: $newValue <- $oldValue")
            GFX.check()
            when (newValue) {
                null -> glDisable(GL_BLEND)
                BlendMode.INHERIT -> {
                    var index = index
                    var self: Any?
                    do {
                        self = values[index--]
                    } while (self == BlendMode.INHERIT)
                    return onChangeValue(self, oldValue)
                }
                is BlendMode -> {
                    glEnable(GL_BLEND)
                    newValue.forceApply()
                }
                is Array<*> -> {
                    glEnable(GL_BLEND)
                    for (i in newValue.indices) {
                        val v = newValue[i] as BlendMode
                        v.forceApply(i)
                    }
                }
                else -> throw IllegalArgumentException()
            }
        }
    }

    val depthMode = object : SecureStack<DepthMode>(DepthMode.ALWAYS) {
        override fun onChangeValue(newValue: DepthMode, oldValue: DepthMode) {
            GFX.check()
            if (newValue.id != 0) {
                glEnable(GL_DEPTH_TEST)
                glDepthFunc(newValue.id)
                val reversedDepth = newValue.reversedDepth
                if (supportsClipControl) {
                    glClipControl(GL_LOWER_LEFT, if (reversedDepth) GL_ZERO_TO_ONE else GL_NEGATIVE_ONE_TO_ONE)
                } else {
                    // does this work??
                    glDepthRange(0.0, 1.0)
                }
                // glDepthRange(-1.0, 1.0)
                // glDepthFunc(GL_LESS)
            } else {
                glDisable(GL_DEPTH_TEST)
            }
        }
    }

    val depthMask = object : SecureStack<Boolean>(true) {
        override fun onChangeValue(newValue: Boolean, oldValue: Boolean) {
            GFX.check()
            glDepthMask(newValue)
        }
    }

    val instanced = object : SecureStack<Boolean>(false) {
        override fun onChangeValue(newValue: Boolean, oldValue: Boolean) {
            // nothing changes on the OpenGL side,
            // just the shaders need to be modified
        }
    }

    /**
     * a flag for shaders whether their animated version (slower) is used
     * */
    val animated = object : SecureStack<Boolean>(false) {
        override fun onChangeValue(newValue: Boolean, oldValue: Boolean) {
            // nothing changes on the OpenGL side,
            // just the shaders need to be modified
        }
    }

    /**
     * a flag for shaders whether their limited-transform version (faster for 10k+ instances) is used
     * */
    val prsTransform = object : SecureStack<Boolean>(false) {
        override fun onChangeValue(newValue: Boolean, oldValue: Boolean) {
            // nothing changes on the OpenGL side,
            // just the shaders need to be modified
        }
    }

    val cullMode = object : SecureStack<CullMode>(CullMode.BOTH) {
        override fun onChangeValue(newValue: CullMode, oldValue: CullMode) {
            GFX.check()
            when (newValue) {
                CullMode.BOTH -> {
                    glDisable(GL_CULL_FACE)
                }
                CullMode.FRONT -> {
                    glEnable(GL_CULL_FACE)
                    glCullFace(GL_FRONT)
                }
                CullMode.BACK -> {
                    glEnable(GL_CULL_FACE)
                    glCullFace(GL_BACK)
                }
            }
        }
    }

    @Suppress("unused")
    val stencilTest = object : SecureStack<Boolean>(false) {
        override fun onChangeValue(newValue: Boolean, oldValue: Boolean) {
            if (newValue) glEnable(GL_STENCIL_TEST)
            else glDisable(GL_STENCIL_TEST)
        }
    }

    val scissorTest = object : SecureStack<Boolean>(false) {
        override fun onChangeValue(newValue: Boolean, oldValue: Boolean) {
            if (newValue) glEnable(GL_SCISSOR_TEST)
            else glDisable(GL_SCISSOR_TEST)
        }
    }

    /**
     * render without blending and without depth test
     * */
    fun <V> renderPurely(render: () -> V): V {
        return blendMode.use(null) {
            depthMode.use(DepthMode.ALWAYS, render)
        }
    }

    /**
     * render without blending and without depth test
     * */
    fun <V> renderPurely2(render: () -> V): V {
        return blendMode.use(null) {
            depthMask.use(false) {
                depthMode.use(DepthMode.ALWAYS, render)
            }
        }
    }

    /**
     * render with back-to-front alpha blending and without depth test
     * */
    fun <V> renderDefault(render: () -> V): V {
        return blendMode.use(BlendMode.DEFAULT) {
            depthMode.use(DepthMode.ALWAYS, render)
        }
    }

    // maximum expected depth for OpenGL operations
    // could be changed, if needed...
    private const val maxSize = 512
    val renderers = Array<Renderer>(maxSize) { colorRenderer }

    val currentRenderer get() = renderers[framebuffer.index]
    val currentBuffer get() = framebuffer.values[framebuffer.index]

    val xs = IntArray(maxSize)
    val ys = IntArray(maxSize)
    val ws = IntArray(maxSize)
    val hs = IntArray(maxSize)
    val changeSizes = BooleanArray(maxSize)

    val framebuffer = object : SecureStack<IFramebuffer>(NullFramebuffer) {
        override fun onChangeValue(newValue: IFramebuffer, oldValue: IFramebuffer) {
            Frame.bind(newValue, changeSizes[index], xs[index], ys[index], ws[index], hs[index])
        }
    }

    fun useFrame(
        buffer: IFramebuffer,
        renderer: Renderer,
        render: () -> Unit
    ) = useFrame(0, 0, -1, -1, buffer, renderer, render)

    fun useFrame(
        x: Int, y: Int, w: Int, h: Int,
        buffer: IFramebuffer, renderer: Renderer, render: () -> Unit
    ) = useFrame(x, y, w, h, false, buffer, renderer, render)

    private fun useFrame(
        x: Int, y: Int, w: Int, h: Int, changeSize: Boolean,
        buffer: IFramebuffer, renderer: Renderer, render: () -> Unit
    ) {
        if ((w > 0 && h > 0) || (w == -1 && h == -1)) {
            val index = framebuffer.size
            if (index >= xs.size) {
                throw StackOverflowError("Reached recursion limit for useFrame()")
            }
            xs[index] = x
            ys[index] = y
            ws[index] = w
            hs[index] = h
            changeSizes[index] = changeSize
            buffer.use(index, renderer, render)
        } else buffer.ensure()
    }

    fun useFrame(renderer: Renderer, render: () -> Unit) =
        useFrame(currentBuffer, renderer, render)

    fun useFrame(buffer: IFramebuffer, render: () -> Unit) =
        useFrame(buffer, currentRenderer, render)

    fun useFrame(
        w: Int, h: Int, changeSize: Boolean,
        buffer: IFramebuffer, renderer: Renderer, render: () -> Unit
    ) = useFrame(0, 0, w, h, changeSize, buffer, renderer, render)

    fun useFrame(w: Int, h: Int, changeSize: Boolean, buffer: IFramebuffer, render: () -> Unit) =
        useFrame(w, h, changeSize, buffer, currentRenderer, render)

    fun useFrame(w: Int, h: Int, changeSize: Boolean, render: () -> Unit) =
        useFrame(w, h, changeSize, currentBuffer, currentRenderer, render)

    fun useFrame(x: Int, y: Int, w: Int, h: Int, render: () -> Unit) =
        useFrame(x, y, w, h, currentBuffer, currentRenderer, render)

    fun useFrame(
        x: Int, y: Int, w: Int, h: Int,
        buffer: IFramebuffer, render: () -> Unit
    ) = useFrame(x, y, w, h, buffer, currentRenderer, render)

    fun useFrame(
        w: Int, h: Int, changeSize: Boolean,
        renderer: Renderer, render: () -> Unit
    ) = useFrame(w, h, changeSize, currentBuffer, renderer, render)

    fun useFrame(
        x: Int, y: Int, w: Int, h: Int,
        renderer: Renderer, render: () -> Unit
    ) = useFrame(x, y, w, h, currentBuffer, renderer, render)

    private val tmp = Framebuffer("tmp", 1, 1, 1, 0, false, DepthBufferType.NONE)

    /**
     * render onto that texture
     * */
    fun useFrame(texture: Texture2D, level: Int, render: (IFramebuffer) -> Unit) {
        tmp.width = texture.width
        tmp.height = texture.height
        if (tmp.pointer == 0 || tmp.session != session) {
            tmp.pointer = glGenFramebuffers()
        }
        useFrame(tmp) {
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, texture.target, texture.pointer, level)
            Framebuffer.drawBuffers1(0)
            tmp.checkIsComplete()
            render(tmp)
        }
    }
}