package me.anno.video.formats.cpu

import me.anno.image.Image
import me.anno.io.files.FileReference
import me.anno.video.LastFrame
import me.anno.video.formats.FrameReader
import java.io.IOException
import java.io.InputStream

class CPUFrameReader(
    file: FileReference, frame0: Int, bufferLength: Int,
    nextFrameCallback: (Image) -> Unit,
    finishedCallback: (List<Image>) -> Unit
) : FrameReader<Image>(file, frame0, bufferLength, nextFrameCallback, finishedCallback) {

    override fun readFrame(w: Int, h: Int, input: InputStream): Image? {
        try {
            val frame = when (codec) {
                // yuv
                "I420" -> I420Frame
                "444P" -> I444Frame
                // rgb
                "ARGB" -> ARGBFrame
                "BGRA" -> BGRAFrame
                "RGBA" -> RGBAFrame
                "RGB" -> RGBFrame
                "BGR", "BGR[24]" -> BGRFrame
                // bw
                "Y4", "Y800" -> Y4Frame // seems correct, awkward, that it has the same name
                else -> I420Frame // throw RuntimeException("Unsupported Codec $codec for $file")
            }
            return frame.load(w, h, input)
        } catch (_: LastFrame) {

        } catch (_: IOException) {

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun destroy() {
        frames.clear()
        isDestroyed = true
    }
}