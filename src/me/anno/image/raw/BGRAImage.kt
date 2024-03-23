package me.anno.image.raw

import me.anno.gpu.framebuffer.TargetType.Companion.Float16xI
import me.anno.gpu.framebuffer.TargetType.Companion.Float32xI
import me.anno.gpu.framebuffer.TargetType.Companion.UInt8xI
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.TextureHelper
import me.anno.image.Image
import me.anno.maths.Maths.max
import me.anno.utils.Color.convertABGR2ARGB
import me.anno.utils.structures.Callback
import org.lwjgl.opengl.GL46C.GL_FLOAT
import org.lwjgl.opengl.GL46C.GL_HALF_FLOAT

/**
 * the easiest check whether an image has R and B channels inverted: if so, this will look correct
 * */
class BGRAImage(val base: Image) :
    Image(base.width, base.height, base.numChannels, base.hasAlphaChannel) {

    override var width: Int
        get() = base.width
        set(value) {
            base.width = value
        }

    override var height: Int
        get() = base.height
        set(value) {
            base.height = value
        }

    override fun getRGB(index: Int): Int {
        // argb -> abgr
        return convertABGR2ARGB(base.getRGB(index))
    }

    override fun createTexture(
        texture: Texture2D, sync: Boolean, checkRedundancy: Boolean,
        callback: Callback<ITexture2D>
    ) {
        if (base is GPUImage) {
            // if source has float precision, use that
            val tex = base.texture
            val useFP = when (TextureHelper.getNumberType(tex.internalFormat)) {
                GL_HALF_FLOAT -> Float16xI
                GL_FLOAT -> Float32xI
                else -> UInt8xI
            }
            val type = useFP[max(base.numChannels - 1, 0)]
            TextureMapper.mapTexture(base.texture, texture, "bgra", type, callback)
        } else super.createTexture(texture, sync, checkRedundancy, callback)
    }
}