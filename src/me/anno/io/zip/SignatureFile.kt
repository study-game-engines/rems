package me.anno.io.zip

import me.anno.io.files.Signature
import me.anno.utils.types.InputStreams.readNBytes2
import java.io.InputStream

interface SignatureFile {

    var signature: Signature?

    companion object {
        @JvmStatic
        fun setDataAndSignature(file: InnerFile, getInputStream: () -> InputStream) {
            if (!file.isDirectory) {
                file as SignatureFile
                if (file.size in 1..InnerFolderCache.sizeLimit) {
                    file.data = getInputStream().buffered().use { it.readBytes() }
                    file.signature = Signature.find(file.data!!)
                } else {
                    val bytes = getInputStream().buffered().use { it.readNBytes2(Signature.sampleSize, false) }
                    file.signature = Signature.find(bytes)
                }
            }
        }
    }

}