package me.anno.image

import me.anno.cache.AsyncCacheData
import me.anno.utils.structures.Callback
import me.anno.gpu.texture.TextureCache
import me.anno.gpu.texture.TextureLib.blackTexture
import me.anno.gpu.texture.TextureLib.missingColors
import me.anno.gpu.texture.TextureLib.missingTexture
import me.anno.gpu.texture.TextureLib.whiteTexture
import me.anno.image.raw.*
import me.anno.io.MediaMetadata
import me.anno.io.files.BundledRef
import me.anno.io.files.FileReference
import me.anno.io.files.Signature
import me.anno.io.files.inner.InnerFolder
import me.anno.io.files.inner.SignatureFile
import me.anno.maths.Maths
import me.anno.utils.OS
import org.apache.commons.imaging.Imaging
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO

/**
 * an easy interface to read any image as rgba and individual channels
 * */
object ImageReader {

    private val LOGGER = LogManager.getLogger(ImageReader::class)
    private val missingImage = IntImage(2, 2, missingColors, false)

    var tryFFMPEG: ((file: FileReference, signature: String?, forGPU: Boolean, callback: Callback<Image>) -> Unit)? =
        null

    @JvmStatic
    fun readAsFolder(file: FileReference, callback: Callback<InnerFolder>) {

        val folder = InnerFolder(file)

        // add the most common swizzles: r,g,b,a
        createComponent(file, folder, "r.png", "r", false)
        createComponent(file, folder, "g.png", "g", false)
        createComponent(file, folder, "b.png", "b", false)
        createComponent(file, folder, "a.png", "a", false)

        // bgra
        createComponent(file, folder, "bgra.png") {
            if (it is BGRAImage) it.base // bgra.bgra = rgba
            else BGRAImage(it)
        }

        // inverted components
        createComponent(file, folder, "1-r.png", "r", true)
        createComponent(file, folder, "1-g.png", "g", true)
        createComponent(file, folder, "1-b.png", "b", true)
        createComponent(file, folder, "1-a.png", "a", true)

        // white with transparency, black with transparency (overriding color)
        createAlphaMask(file, folder, "111a.png", false)
        createAlphaMask(file, folder, "000a.png", true)

        // grayscale, if not only a single channel
        createComponent(file, folder, "grayscale.png") {
            if (it.numChannels > 1) GrayscaleImage(it)
            else it
        }

        // rgb without alpha, if alpha exists
        createComponent(file, folder, "rgb.png") {
            if (it.hasAlphaChannel) OpaqueImage(it)
            else it
        }

        if (file.lcExtension == "ico") {
            try {
                val icoReaderClass = this::class.java.classLoader
                    .loadClass("net.sf.image4j.codec.ico.ICOReader")
                val method = icoReaderClass.getMethod("readAllLayers", FileReference::class.java)
                Signature.findName(file) { sign ->
                    if (sign == null || sign == "ico") {
                        file.inputStream { it, exc ->
                            if (it != null) {
                                val layers = method.invoke(null, it) as List<*>
                                for (index in layers.indices) {
                                    val layer = layers[index] as? Image ?: break
                                    folder.createImageChild("layer$index", layer)
                                }
                                it.close()
                                callback.ok(folder)
                            } else {
                                exc?.printStackTrace()
                                callback.ok(folder)
                            }
                        }
                    } else callback.ok(folder)
                }
                return // we're done, don't call callback twice
            } catch (e: ClassNotFoundException) {
                LOGGER.warn("Can't find ICOReader to load layers for .ico file")
            } catch (e: NoSuchMethodException) {
                LOGGER.warn("Can't find ICOReader.readAllLayers to load layers for .ico file")
            }
        }

        callback.ok(folder)
    }

    @JvmStatic
    private fun createComponent(file: FileReference, folder: InnerFolder, name: String, createImage: (Image) -> Image) {
        folder.createLazyImageChild(name, lazy {
            val src = ImageCache[file, false] ?: missingImage
            createImage(src)
        }, {
            val src = TextureCache[file, false] ?: missingTexture
            createImage(GPUImage(src))
        })
    }

    @JvmStatic
    private fun createComponent(
        file: FileReference, folder: InnerFolder, name: String,
        swizzle: String, inverse: Boolean
    ) {
        when (swizzle.length) {
            1 -> createComponent(file, folder, name) { srcImage ->
                when {
                    (swizzle == "a" && !srcImage.hasAlphaChannel) -> GPUImage(if (inverse) blackTexture else whiteTexture)
                    (swizzle == "b" && srcImage.numChannels < 3) || (swizzle == "g" && srcImage.numChannels < 2) ->
                        GPUImage(if (inverse) whiteTexture else blackTexture)
                    else -> ComponentImage(srcImage, inverse, swizzle[0])
                }
            }
            else -> throw NotImplementedError(swizzle)
        }
    }

    @JvmStatic
    private fun createAlphaMask(file: FileReference, folder: InnerFolder, name: String, black: Boolean) {
        createComponent(file, folder, name) { srcImage ->
            if (srcImage.hasAlphaChannel) {
                val color1 = if (black) 0 else 0xffffff
                AlphaMaskImage(srcImage, false, 'a', color1)
            } else {
                GPUImage(if (black) blackTexture else whiteTexture)
            }
        }
    }

    private fun shouldUseFFMPEG(signature: String?, file: FileReference): Boolean {
        if (OS.isWeb) return false // uncomment, when we support FFMPEG in the browser XD
        return signature == "dds" || signature == "media" || file.lcExtension == "webp"
    }

    private fun shouldIgnore(signature: String?): Boolean {
        return when (signature) {
            "rar", "bz2", "zip", "tar", "gzip", "xz", "lz4", "7z", "xar", "oar", "java", "text",
            "wasm", "ttf", "woff1", "woff2", "shell", "xml", "svg", "exe",
            "vox", "fbx", "gltf", "obj", "blend", "mesh-draco", "md2", "md5mesh", "dae",
            "yaml" -> true
            else -> false
        }
    }

    fun readImage(file: FileReference, data: AsyncCacheData<Image?>, forGPU: Boolean) {
        if (file is ImageReadable) {
            data.value = if (forGPU) file.readGPUImage() else file.readCPUImage()
        } else if (file is BundledRef || (file !is SignatureFile && file.length() < 10_000_000L)) { // < 10MB -> read directly
            file.readBytes { bytes, exc ->
                exc?.printStackTrace()
                if (bytes != null) {
                    readImage(file, data, bytes, forGPU)
                } else {
                    data.value = null
                    data.hasValue = true
                }
            }
        } else Signature.findName(file) { signature ->
            readImage(file, data, signature, forGPU)
        }
    }

    private fun readImage(file: FileReference, data: AsyncCacheData<Image?>, bytes: ByteArray, forGPU: Boolean) {
        val signature = Signature.findName(bytes)
        val tryFFMPEG = tryFFMPEG
        if (shouldIgnore(signature)) {
            data.value = null
        } else if (tryFFMPEG != null && shouldUseFFMPEG(signature, file)) {
            tryFFMPEG(file, signature, forGPU) { it, e ->
                data.value = it
                e?.printStackTrace()
            }
        } else {
            val reader = ImageCache.byteReaders[signature] ?: ImageCache.byteReaders[file.lcExtension]
            data.value = if (reader != null) reader(bytes) else tryGeneric(file, bytes)
        }
    }

    private fun readImage(file: FileReference, data: AsyncCacheData<Image?>, signature: String?, forGPU: Boolean) {
        val tryFFMPEG = tryFFMPEG
        if (shouldIgnore(signature)) {
            data.value = null
        } else if (tryFFMPEG != null && shouldUseFFMPEG(signature, file)) {
            tryFFMPEG(file, signature, forGPU) { it, e ->
                data.value = it
                e?.printStackTrace()
            }
        } else {
            val reader = ImageCache.fileReaders[signature] ?: ImageCache.fileReaders[file.lcExtension]
            if (reader != null) reader(file) { it, e ->
                e?.printStackTrace()
                data.value = it
            } else tryGeneric(file) { it, e ->
                e?.printStackTrace()
                data.value = it
            }
        }
    }

    fun frameIndex(meta: MediaMetadata): Int {
        return Maths.min(20, (meta.videoFrameCount - 1) / 3)
    }

    private fun tryGeneric(file: FileReference, callback: Callback<Image>) {
        file.inputStream { it, exc ->
            if (it != null) {
                try {
                    val img = ImageIO.read(it) ?: throw IOException(file.toString())
                    it.close()
                    callback.ok(img.toImage())
                } catch (e: Exception) {
                    it.close()
                    file.inputStream { it2, exc2 ->
                        if (it2 != null) {
                            try {
                                callback.call(Imaging.getBufferedImage(it2).toImage(), null)
                            } catch (e: Exception) {
                                callback.call(null, e)
                            } finally {
                                it2.close()
                            }
                        } else callback.err(exc2)
                    }
                }
            } else callback.err(exc)
        }
    }

    private fun tryGeneric(file: FileReference, bytes: ByteArray): Image? {
        var image = try {
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (image == null) {
            LOGGER.debug("ImageIO failed for {}", file)
            try {
                image = Imaging.getBufferedImage(bytes)
            } catch (e: Exception) {
                // e.printStackTrace()
            }
        }
        if (image == null) {
            LOGGER.debug("Imaging failed for {}", file)
        }
        return image?.toImage()
    }
}