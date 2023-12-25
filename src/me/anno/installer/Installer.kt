package me.anno.installer

import me.anno.Time
import me.anno.gpu.GFX
import me.anno.io.BufferedIO.useBuffered
import me.anno.io.files.FileReference
import me.anno.maths.Maths.SECONDS_TO_NANOS
import me.anno.ui.base.progress.ProgressBar
import me.anno.utils.OS
import me.anno.utils.types.Strings.formatDownload
import me.anno.utils.types.Strings.formatDownloadEnd
import me.anno.video.ffmpeg.FFMPEG
import me.anno.video.ffmpeg.FFMPEG.ffmpegPath
import me.anno.video.ffmpeg.FFMPEG.ffprobePath
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLHandshakeException
import kotlin.concurrent.thread

object Installer {

    @JvmStatic
    private val LOGGER = LogManager.getLogger(Installer::class)

    // on startup check if ffmpeg can be found
    // if not, download it - from our website?

    // check all dependencies

    // where should we put ffmpeg?
    // typically that would be (xyz on Linux, idk, maybe anywhere?, ~/.remsStudio?)
    // put it into ~/.AntonioNoack/RemsStudio?

    // all files need to be checked every time
    @JvmStatic
    fun checkInstall() {
        if (!FFMPEG.isInstalled && OS.isWindows) {
            downloadMaybe("ffmpeg/bin/ffmpeg.exe", ffmpegPath)
            downloadMaybe("ffmpeg/bin/ffprobe.exe", ffprobePath)
        }
    }

    @JvmStatic
    fun downloadMaybe(src: String, dst: FileReference) {
        if (!dst.exists) download(src, dst) {}
        else LOGGER.info("$src already is downloaded :)")
    }

    @JvmStatic
    fun download(fileName: String, dstFile: FileReference, callback: () -> Unit) =
        download(fileName, dstFile, true, callback)

    @JvmStatic
    fun download(fileName: String, dstFile: FileReference, withHttps: Boolean = true, callback: () -> Unit) {
        // change "files" to "files.phychi.com"?
        // create a temporary file, and rename, so we know that we finished the download :)
        val tmp = dstFile.getSibling(dstFile.name + ".tmp")
        thread(name = "Download $fileName") {
            val window = GFX.someWindow
            val progress = window?.addProgressBar(fileName, "Bytes", Double.NaN)
            val protocol = if (withHttps) "https" else "http"
            val name = fileName.replace(" ", "%20")
            val totalURL = "${protocol}://remsstudio.phychi.com/download/${name}"
            try {
                runDownload(URL(totalURL), fileName, dstFile, tmp, progress)
                callback()
            } catch (e: SSLHandshakeException) {
                if (withHttps) {
                    download(fileName, dstFile, false, callback)
                } else {
                    LOGGER.error("Something went wrong with HTTPS :/. Please update Java, or download $totalURL to $dstFile :)")
                    e.printStackTrace()
                }
            } catch (e: IOException) {
                progress?.cancel(false)
                LOGGER.error("Tried to download $fileName from $totalURL to $dstFile, but failed! You can try to do it yourself.")
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    private fun runDownload(
        url: URL, fileName: String, dstFile: FileReference, tmp: FileReference,
        progress: ProgressBar?,
    ) {
        val con = url.openConnection() as HttpURLConnection
        val contentLength = con.contentLength
        if (contentLength > 0L) progress?.total = con.contentLength.toDouble()
        val input = con.inputStream.useBuffered()
        dstFile.getParent()?.tryMkdirs()
        val output = tmp.outputStream().useBuffered()
        val totalLength = con.contentLength.toLong()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var time0 = Time.nanoTime
        var length0 = 0L
        var downloadedLength = 0L
        while (true) {
            val length = input.read(buffer)
            if (length < 0) break
            length0 += length
            downloadedLength += length
            output.write(buffer, 0, length)
            val time1 = Time.nanoTime
            val dt = time1 - time0
            progress?.progress = downloadedLength.toDouble()
            if (dt > SECONDS_TO_NANOS) {
                LOGGER.info(formatDownload(fileName, dt, length0, downloadedLength, totalLength))
                time0 = time1
                length0 = 0
            }
        }
        output.close()
        tmp.renameTo(dstFile)
        progress?.finish(true)
        LOGGER.info(formatDownloadEnd(fileName, dstFile))
    }

    @JvmStatic
    fun download(
        fileName: String,
        srcFile: FileReference,
        dstFile: FileReference,
        callback: () -> Unit
    ) {
        // change "files" to "files.phychi.com"?
        // create a temporary file, and rename, so we know that we finished the download :)
        val tmp = dstFile.getSibling(dstFile.name + ".tmp")
        thread(name = "Download $fileName") {
            val window = GFX.someWindow
            val progress = window?.addProgressBar(fileName, "Bytes", Double.NaN)
            try {
                runDownload(URL(srcFile.absolutePath), fileName, dstFile, tmp, progress)
                callback()
            } catch (e: IOException) {
                progress?.cancel(false)
                LOGGER.error("Tried to download $fileName from $srcFile to $dstFile, but failed! You can try to do it yourself.")
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun uninstall() {

        // to do show a window
        // to do ask if the config should be deleted
        // to do ask if all (known, latest) projects should be erased

        // to do ask if ffmpeg shall be deleted, if it's not in the default install directory
        // to do put config into that default install directory???
        Uninstaller.uninstall()
    }
}