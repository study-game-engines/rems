package me.anno.image.raw

import me.anno.gpu.texture.Texture2D
import me.anno.image.Image
import me.anno.io.files.FileReference
import me.anno.utils.LOGGER
import me.anno.video.formats.gpu.GPUFrame

class GPUFrameImage(val frame: GPUFrame, numChannels: Int, hasAlphaChannel: Boolean) :
    Image(frame.width, frame.height, numChannels, hasAlphaChannel) {

    constructor(frame: GPUFrame, numChannels: Int) : this(frame, numChannels, numChannels > 3)
    constructor(frame: GPUFrame) : this(frame, frame.numChannels)

    override fun getRGB(index: Int): Int {
        val msg = "GPUFrameImage.getRGB() is extremely inefficient!!!"
        LOGGER.warn(msg, RuntimeException(msg))
        val image = createIntImage()
        val color = image.getRGB(index)
        image.destroy()
        return color
    }

    override fun createIntImage(): IntImage {
        val texture = frame.toTexture()
        val image = texture.createImage(false, hasAlphaChannel)
        texture.destroy()
        return image
    }

    override fun write(dst: FileReference) {
        createIntImage().write(dst)
    }

    override fun createTexture(texture: Texture2D, sync: Boolean, checkRedundancy: Boolean) {
        frame.toTexture(texture)
    }

    override fun toString(): String {
        return "GPUImage { $frame, $numChannels ch, ${if (hasAlphaChannel) "alpha" else "opaque"} }"
    }
}