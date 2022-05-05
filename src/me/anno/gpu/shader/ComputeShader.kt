package me.anno.gpu.shader

import me.anno.gpu.GFX
import me.anno.gpu.hidden.HiddenOpenGLContext
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.Texture3D
import me.anno.maths.Maths.ceilDiv
import org.apache.logging.log4j.LogManager
import org.joml.Vector2i
import org.joml.Vector3i
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL43.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
class ComputeShader(
    shaderName: String,
    val version: Int,
    val groupSize: Vector3i,
    val source: String
) : OpenGLShader(shaderName) {

    constructor(shaderName: String, localSize: Vector2i, source: String) :
            this(shaderName, Vector3i(localSize, 1), source)

    constructor(shaderName: String, localSize: Vector3i, source: String) :
            this(shaderName, 430, localSize, source)

    override fun compile() {

        val program = glCreateProgram()
        updateSession()

        checkGroupSizeBounds()
        val source = "" +
                "#version $version\n" +
                "layout(local_size_x = ${groupSize.x}, local_size_y = ${groupSize.y}, local_size_z = ${groupSize.z}) in;\n" +
                source
        val shader = compile(name, program, GL_COMPUTE_SHADER, source)
        glLinkProgram(program)
        postPossibleError(name, program, false, source)
        glDeleteShader(shader)
        logShader(name, source)

        GFX.check()

        this.program = program // only assign this value, when no error has occurred

    }

    override fun sourceContainsWord(word: String): Boolean {
        return word in source
    }

    fun checkGroupSizeBounds() {
        if (groupSize.x < 1) groupSize.x = 1
        if (groupSize.y < 1) groupSize.y = 1
        if (groupSize.z < 1) groupSize.z = 1
        val groupSize = groupSize.x * groupSize.y * groupSize.z
        val maxGroupSize = stats[3]
        if (groupSize > maxGroupSize) throw RuntimeException(
            "Group size too large: ${this.groupSize.x} x ${this.groupSize.y} x ${this.groupSize.z} > $maxGroupSize"
        )
    }

    fun bindTexture(slot: Int, texture: Texture2D, mode: ComputeTextureMode) {
        Companion.bindTexture(slot, texture, mode)
    }

    /**
     * for array textures to bind a single layer
     * */
    fun bindTexture(slot: Int, texture: Texture2D, mode: ComputeTextureMode, layer: Int) {
        bindTexture(slot, texture, mode, layer)
    }

    fun bindTexture(slot: Int, texture: Texture3D, mode: ComputeTextureMode) {
        Companion.bindTexture(slot, texture, mode)
    }

    companion object {

        private val LOGGER = LogManager.getLogger(ComputeShader::class)

        @JvmStatic
        fun main(args: Array<String>) {
            HiddenOpenGLContext.createOpenGL()
            LOGGER.info(stats)
        }

        val stats by lazy {
            val tmp = IntArray(1)
            GL30.glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0, tmp)
            val sx = tmp[0]
            GL30.glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1, tmp)
            val sy = tmp[0]
            GL30.glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2, tmp)
            val sz = tmp[0]
            glGetIntegerv(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, tmp)
            val maxUnitsPerGroup = tmp[0]
            LOGGER.info("Max compute group count: $sx x $sy x $sz") // 65k³
            LOGGER.info("Max units per group: $maxUnitsPerGroup") // 1024
            intArrayOf(sx, sy, sz, maxUnitsPerGroup)
        }

        // texture type could be derived from the shader and texture -> verify it?
        // todo can we dynamically create shaders for the cases that we need? probably best :)
        fun bindTexture(slot: Int, texture: Texture2D, mode: ComputeTextureMode) {
            glBindImageTexture(slot, texture.pointer, 0, true, 0, mode.code, texture.internalFormat)
        }

        /**
         * for array textures to bind a single layer
         * */
        fun bindTexture(slot: Int, texture: Texture2D, mode: ComputeTextureMode, layer: Int) {
            glBindImageTexture(slot, texture.pointer, 0, false, layer, mode.code, texture.internalFormat)
        }

        fun bindTexture(slot: Int, texture: Texture3D, mode: ComputeTextureMode) {
            glBindImageTexture(slot, texture.pointer, 0, true, 0, mode.code, texture.internalFormat)
        }

    }

    fun runBySize(width: Int, height: Int, depth: Int = 1) {
        runByGroups(ceilDiv(width, groupSize.x), ceilDiv(height, groupSize.y), ceilDiv(depth, groupSize.z))
    }

    fun runByGroups(widthGroups: Int, heightGroups: Int, depthGroups: Int = 1) {
        if (lastProgram != program) {
            glUseProgram(program)
            lastProgram = program
        }
        val maxGroupSize = stats
        if (widthGroups > maxGroupSize[0] || heightGroups > maxGroupSize[1] || depthGroups > maxGroupSize[2]) {
            throw IllegalArgumentException(
                "Group count out of bounds: ($widthGroups x $heightGroups x $depthGroups x GroupSize) > " +
                        "(${maxGroupSize.joinToString(" x ")})"
            )
        }
        glDispatchCompute(widthGroups, heightGroups, depthGroups)
        // currently true, but that might change, if we just write to data buffers or similar
        Texture2D.wasModifiedInComputePipeline = true
    }

}