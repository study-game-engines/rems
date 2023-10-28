package me.anno.video.formats.cpu

import me.anno.image.Image
import me.anno.image.raw.ByteImage
import me.anno.utils.types.InputStreams.readNBytes2
import java.io.InputStream

object ARGBFrame : CPUFrame() {
    override fun load(w: Int, h: Int, input: InputStream): Image {
        val bytes = input.readNBytes2(w * h * 4, true)
        val image = ByteImage(w, h, ByteImage.Format.ARGB, bytes)
        image.hasAlphaChannel = (bytes.indices step 4).any { bytes[it].toInt() != -1 }
        return image
    }
}