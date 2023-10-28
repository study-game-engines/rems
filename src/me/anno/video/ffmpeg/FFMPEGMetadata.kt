package me.anno.video.ffmpeg

import me.anno.audio.AudioReadable
import me.anno.cache.CacheSection
import me.anno.cache.ICacheData
import me.anno.image.ImageReadable
import me.anno.image.gimp.GimpImage
import me.anno.image.tar.TGAImage
import me.anno.io.files.FileFileRef
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.Signature
import me.anno.io.files.WebRef
import me.anno.io.json.JsonReader
import me.anno.utils.OS
import me.anno.utils.Warning.unused
import me.anno.utils.process.BetterProcessBuilder
import me.anno.utils.strings.StringHelper.shorten
import me.anno.utils.structures.tuples.IntPair
import me.anno.utils.types.AnyToDouble.getDouble
import me.anno.utils.types.AnyToInt.getInt
import me.anno.utils.types.AnyToLong.getLong
import me.anno.utils.types.Strings.formatTime
import me.anno.utils.types.Strings.parseTime
import me.saharnooby.qoi.QOIImage
import net.sf.image4j.codec.ico.ICOReader
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.roundToInt

class FFMPEGMetadata(val file: FileReference, signature: String?) : ICacheData {

    var duration = 0.0

    var hasAudio = false
    var hasVideo = false

    var audioStartTime = 0.0
    var audioSampleRate = 0
    var audioDuration = 0.0
    var audioSampleCount = 0L // 24h * 3600s/h * 48k = 4B -> Long
    var audioChannels = 0

    var videoStartTime = 0.0
    var videoFPS = 0.0
    var videoDuration = 0.0
    var videoWidth = 0
    var videoHeight = 0
    var videoFrameCount = 0

    var ready = true

    override fun toString(): String {
        return "FFMPEGMetadata(file: ${file.absolutePath.shorten(200)}, audio: ${
            if (hasAudio) "[$audioSampleRate Hz, $audioChannels ch]" else "false"
        }, video: ${
            if (hasVideo) "[$videoWidth x $videoHeight, $videoFrameCount]" else "false"
        }, duration: ${duration.formatTime(3)}), channels: $audioChannels"
    }

    init {
        if (file is AudioReadable) {
            hasAudio = true
            audioChannels = file.channels
            audioSampleRate = file.sampleRate
            audioSampleCount = file.sampleCount
            audioDuration = file.duration
            duration = audioDuration
        } else if (file is ImageReadable && file.hasInstantGPUImage()) {
            val image = file.readGPUImage()
            setImage(image.width, image.height)
        } else if (file is ImageReadable) {
            val image = file.readCPUImage()
            setImage(image.width, image.height)
        } else when (val signature1 = signature ?: Signature.findNameSync(file)) {
            "gimp" -> {
                // Gimp files are a special case, which is not covered by FFMPEG
                ready = false
                file.inputStream { it, exc ->
                    if (it != null) {
                        setImage(GimpImage.findSize(it))
                    } else exc?.printStackTrace()
                    ready = true
                }
            }
            "qoi" -> {
                // we have a simple reader, so use it :)
                ready = false
                file.inputStream { it, exc ->
                    if (it != null) {
                        setImage(QOIImage.findSize(it))
                    } else exc?.printStackTrace()
                    ready = true
                }
            }
            // only load ffmpeg for ffmpeg files
            "gif", "media", "dds" -> {
                if (!OS.isAndroid && (file is FileFileRef || file is WebRef)) {
                    loadFFMPEG()
                }
            }
            "png", "jpg", "psd", "exr", "webp" -> {
                // webp supports video, but if so, FFMPEG doesn't seem to support it -> whatever, use ImageIO :)
                for (reader in ImageIO.getImageReadersBySuffix(signature1)) {
                    try {
                        file.inputStreamSync().use { input: InputStream ->
                            reader.input = ImageIO.createImageInputStream(input)
                            setImage(reader.getWidth(reader.minIndex), reader.getHeight(reader.minIndex))
                        }
                        break
                    } catch (_: IOException) {
                    } finally {
                        reader.dispose()
                    }
                }
            }
            "ico" -> setImage(file.inputStreamSync().use { input: InputStream -> ICOReader.findSize(input) })
            "", null -> {
                when (file.lcExtension) {
                    "tga" -> setImage(file.inputStreamSync().use { stream: InputStream -> TGAImage.findSize(stream) })
                    "ico" -> setImage(file.inputStreamSync().use { stream: InputStream -> ICOReader.findSize(stream) })
                    // else unknown
                    else -> LOGGER.debug("${file.absolutePath.shorten(200)} has unknown extension and signature: '$signature1'")
                }
            }
            // todo xml/svg
            else -> LOGGER.debug("${file.absolutePath.shorten(200)}'s signature wasn't registered in FFMPEGMetadata.kt: '$signature1'")
        }
    }

    fun setImage(wh: IntPair) {
        setImage(wh.first, wh.second)
    }

    fun setImage(w: Int, h: Int) {
        hasVideo = true
        videoWidth = w
        videoHeight = h
        videoFrameCount = 1
        duration = Double.POSITIVE_INFINITY // actual value isn't really well-defined
    }

    fun loadFFMPEG() {

        val args = listOf(
            "-v", "quiet",
            "-print_format", "json",
            "-show_format",
            "-show_streams",
            "-print_format", "json",
            file.absolutePath
        )

        val builder = BetterProcessBuilder(FFMPEG.ffprobePathString, args.size, true)
        builder += args

        val process = builder.start()

        // get and parse the data :)
        val data = JsonReader(process.inputStream.buffered()).readObject()
        val streams = data["streams"] as? ArrayList<*> ?: ArrayList<Any?>()
        val format = data["format"] as? HashMap<*, *> ?: HashMap<String, Any?>()

        // critical, not working 001.gif file data from ffprobe:
        // works in Windows, just not Linux
        // todo transfer ffmpeg to Java? maybe ffmpeg is an older version on Linux?
        // {streams=[
        //      {
        //          pix_fmt=bgra,
        //          time_base=1/100,
        //          coded_height=270,
        //          level=-99,
        //          r_frame_rate=12000/1001,
        //          index=0,
        //          codec_name=gif,
        //          sample_aspect_ratio=0:1,
        //          disposition=
        //              {dub=0, karaoke=0, default=0, original=0, visual_impaired=0, forced=0, attached_pic=0, timed_thumbnails=0, comment=0, hearing_impaired=0, lyrics=0, clean_effects=0 },
        //          codec_tag=0x0000,
        //          has_b_frames=0,
        //          refs=1,
        //          codec_time_base=1/12,
        //          width=480,
        //          display_aspect_ratio=0:1,
        //          codec_tag_string=[0][0][0][0],
        //          coded_width=480,
        //          avg_frame_rate=12/1,
        //          codec_type=video,
        //          codec_long_name=GIF (Graphics Interchange Format),
        //          height=270
        //      }
        //  ],
        //  format={
        //      filename=/home/antonio/Pictures/Anime/001.gif,
        //      size=3414192,
        //      probe_score=100,
        //      nb_programs=0,
        //      format_long_name=CompuServe Graphics Interchange Format (GIF),
        //      nb_streams=1,
        //      format_name=gif
        //  }}

        duration = format["duration"]?.toString()?.toDouble() ?: getDurationIfMissing(file)

        val audio = streams.firstOrNull {
            (it as HashMap<*, *>)["codec_type"].toString().equals("audio", true)
        } as? HashMap<*, *>

        if (audio != null) {
            hasAudio = true
            audioStartTime = getDouble(audio["start_time"], 0.0)
            audioDuration = getDouble(audio["duration"], duration)
            audioSampleRate = getInt(audio["sample_rate"], 20)
            audioSampleCount = getLong(audio["duration_ts"], (audioSampleRate * audioDuration).toLong())
            audioChannels = getInt(audio["channels"], 1)
        }

        val video = streams.firstOrNull {
            (it as HashMap<*, *>)["codec_type"].toString().equals("video", true)
        } as? HashMap<*, *>

        if (video != null) {

            hasVideo = true
            videoStartTime = getDouble(video["start_time"], 0.0)
            videoDuration = getDouble(video["duration"], duration)
            videoFrameCount = getInt(video["nb_frames"], 0)
            videoWidth = getInt(video["width"], 0)
            videoHeight = getInt(video["height"], 0)
            videoFPS = video["r_frame_rate"]?.toString()?.parseFraction() ?: 30.0

            if (videoFrameCount == 0) {
                if (videoDuration > 0.0) {
                    videoFrameCount = ceil(videoDuration * videoFPS).toInt()
                    LOGGER.info("Frame count was 0, corrected it to $videoFrameCount = $videoDuration * $videoFPS")
                }
            } else {
                val expectedFrameCount = (videoDuration * videoFPS).roundToInt()
                if (expectedFrameCount * 10 !in videoFrameCount * 9..videoFrameCount * 11) {
                    // something is wrong
                    // nb_frames is probably correct
                    LOGGER.warn("$file: Frame Count / Frame Rate / Video Duration incorrect! $videoDuration s * $videoFPS fps is not $videoFrameCount frames")
                    videoDuration = duration // could be incorrect
                    videoFPS = videoFrameCount / videoDuration
                    LOGGER.warn("$file: Corrected by setting duration to $duration s and fps to $videoFPS")
                }
            }

        }

        LOGGER.info("Loaded info about $file: $duration * $videoFPS = $videoFrameCount frames / $audioDuration * $audioSampleRate = $audioSampleCount samples")

    }

    // not working for the problematic file 001.gif
    fun getDurationIfMissing(file: FileReference): Double {

        LOGGER.warn("Duration is missing for $file")

        val args = listOf(
            // ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 input.mp4
            "-i", file.absolutePath, "-f", "null", "-"
        )

        val builder = BetterProcessBuilder(FFMPEG.ffmpegPathString, args.size, true)
        builder += args

        val process = builder.start()

        // get and parse the data :)
        val bytes = process.inputStream.readBytes()
        val data = String(bytes)
        if (data.isEmpty()) return 0.0
        else LOGGER.info("Duration, because missing: $data")
        val time = data.split("time=")[1].split(" ")[0]
        // frame=206723 fps=1390 q=-0.0 Lsize=N/A time=00:57:28.87 bitrate=N/A speed=23.2x
        return time.parseTime()

    }

    fun String.parseFraction(): Double {
        val i = indexOf('/')
        if (i < 0) return toDouble()
        val a = substring(0, i).trim().toDouble()
        val b = substring(i + 1).trim().toDouble()
        return a / b
    }

    override fun destroy() {}

    companion object {

        @JvmStatic
        private val LOGGER = LogManager.getLogger(FFMPEGMetadata::class)

        @JvmStatic
        private val metadataCache = CacheSection("Metadata")

        @JvmStatic
        private fun createMetadata(file: FileReference, i: Long): FFMPEGMetadata {
            unused(i)
            return FFMPEGMetadata(file, null)
        }

        @JvmStatic
        private fun createMetadata(file: FileReference, signature: String?): FFMPEGMetadata {
            return FFMPEGMetadata(file, signature ?: "")
        }

        @JvmStatic
        fun getMeta(path: String, async: Boolean): FFMPEGMetadata? {
            return getMeta(getReference(path), async)
        }

        @JvmStatic
        fun getMeta(file: FileReference, async: Boolean): FFMPEGMetadata? {
            return metadataCache.getFileEntry(file, false, 300_000, async, Companion::createMetadata) as? FFMPEGMetadata
        }

        @JvmStatic
        fun getMeta(file: FileReference, signature: String?, async: Boolean): FFMPEGMetadata? {
            return metadataCache.getFileEntry(file, false, 300_000, async) { f, _ ->
                createMetadata(f, signature)
            } as? FFMPEGMetadata
        }
    }

}