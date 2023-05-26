package me.anno.image.jpg

import me.anno.io.files.FileReference
import me.anno.utils.types.InputStreams.readNBytes2

/**
 * This helper objects can extract embedded thumbnails in JPEGs.
 *
 * The standard readers work well and good, but unfortunately,
 * then don't allow me to load the image as a small size;
 * which should definitively be possible
 * Is there a way to read JPG thumbnails from the primary data?
 * Implementing our own reader is MUCH too complicated; JPEG is insane
 * */
object JPGThumbnails {

    fun extractThumbnail(file: FileReference, callback: (ByteArray?) -> Unit) {
        // a small file -> reading the thumbnail is probably not worth it
        if (file.length() < 65536) {
            callback(null)
            return
        }
        // 65k is the max size for an exif section; plus 4k, where we hopefully find the marker
        val maxSize = 65536 + 4096
        file.inputStream(maxSize.toLong()) { it, _ ->
            if (it != null) {
                it.use {
                    val data = it.readNBytes2(maxSize, false)
                    callback(findData(data))
                }
            } else callback(null)
        }
    }

    private fun findStart(data: ByteArray): Int {
        var i = 2
        val li = data.size - 1
        while (i < li) {
            if (data[i] == 0xff.toByte() && data[i + 1] == 0xd8.toByte()) {
                return i
            }
            i++
        }
        return 0
    }

    private fun findData(data: ByteArray): ByteArray? {
        val start = findStart(data)
        val li = data.size - 1
        var i = start
        while (i < li) {
            if (data[i] == 0xff.toByte() && data[i + 1] == 0xd9.toByte()) {
                val end = i + 2
                val bytes = ByteArray(end - start)
                System.arraycopy(data, start, bytes, 0, end - start)
                return bytes
            }
            i++
        }
        return null
    }

}