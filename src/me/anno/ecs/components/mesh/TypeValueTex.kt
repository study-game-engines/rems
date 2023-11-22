package me.anno.ecs.components.mesh

import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Shader
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.TextureCache
import me.anno.io.files.FileReference
import org.apache.logging.log4j.LogManager

class TypeValueTex(
    glslType: GLSLType,
    val source: FileReference,
    val filtering: GPUFiltering,
    val clamping: Clamping,
    val whileMissing: ITexture2D
) : TypeValue(glslType, Unit) {
    companion object {
        private val LOGGER = LogManager.getLogger(TypeValueTex::class)
    }

    override fun bind(shader: Shader, location: Int) {
        when (type) {
            GLSLType.S2D -> {
                val texture = TextureCache[source, false] ?: whileMissing
                texture.bind(location, filtering, clamping)
            }
            else -> LOGGER.warn("$type isn't yet supported")
        }
    }
}