package me.anno.audio

import me.anno.audio.effects.SoundPipeline.Companion.bufferSize
import me.anno.audio.effects.Time
import me.anno.objects.Audio
import me.anno.objects.Camera
import me.anno.objects.modes.LoopingState
import me.anno.video.FFMPEGMetadata
import me.anno.video.FFMPEGMetadata.Companion.getMeta
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor

// only play once, then destroy; it makes things easier
// (on user input and when finally rendering only)

// todo viewing the audio levels is more important than effects
// todo especially editing the audio levels is important (amplitude)


// todo does not work, if the buffers aren't filled fast enough -> always fill them fast enough...
// todo or restart playing...

/**
 * todo audio effects:
 * done better echoing ;)
 * todo velocity frequency change
 * done pitch
 * todo losing high frequencies in the distance
 * done audio becoming quiet in the distance
 * */
abstract class AudioStream(
    val file: File,
    val repeat: LoopingState,
    val startIndex: Long,
    val meta: FFMPEGMetadata,
    val source: Audio,
    val destination: Camera,
    val speed: Double,
    val playbackSampleRate: Int = 48000
) {

    // should be as short as possible for fast calculation
    // should be at least as long as the ffmpeg response time (0.3s for the start of a FHD video)
    companion object {
        fun getIndex(globalTime: Double, speed: Double, playbackSampleRate: Int): Long {
            return floor((globalTime / speed) * playbackSampleRate.toDouble() / bufferSize.toDouble()).toLong()
        }
    }

    constructor(audio: Audio, speed: Double, globalTime: Double, playbackSampleRate: Int, listener: Camera) :
            this(
                audio.file,
                audio.isLooping.value,
                getIndex(globalTime, speed, playbackSampleRate),
                getMeta(audio.file, false)!!,
                audio,
                listener,
                speed,
                playbackSampleRate
            )

    init {
        source.pipeline.audio = source
    }

    fun getTime(index: Long): Time = getTime((index * bufferSize).toDouble() / playbackSampleRate)
    fun getTime(globalTime: Double): Time = Time(globalToLocalTime(globalTime), globalTime)

    // todo is this correct with the speed?
    fun globalToLocalTime(time: Double) = source.getGlobalTime(time * speed)

    var isWaitingForBuffer = AtomicBoolean(false)

    var isPlaying = false

    val buffers = ArrayList<SoundBuffer>()

    fun requestNextBuffer(bufferIndex: Long) {

        isWaitingForBuffer.set(true)
        AudioStreamRaw.taskQueue += {// load all data async

            val byteBuffer = ByteBuffer.allocateDirect(bufferSize * 2 * 2)
                .order(ByteOrder.nativeOrder())
            val stereoBuffer = byteBuffer.asShortBuffer()

            val floats = AudioFXCache.getBuffer(startIndex + bufferIndex, this, false)!!

            val left = floats.first
            val right = floats.second

            // printFloats(left)

            for (i in 0 until bufferSize) {
                stereoBuffer.put(floatToShort(left[i]))
                stereoBuffer.put(floatToShort(right[i]))
            }

            stereoBuffer.position(0)

            onBufferFilled(stereoBuffer, bufferIndex)

        }

    }

    /**
     * the usual function calls d.toInt().toShort(),
     * which causes breaking from max to -max, which ruins audio quality (cracking)
     * this fixes that :)
     * */
    private fun floatToShort(d: Float): Short {
        return when {
            d >= 32767f -> 32767
            d >= -32768f -> d.toInt().toShort()
            else -> -32768
        }
    }

    abstract fun onBufferFilled(stereoBuffer: ShortBuffer, bufferIndex: Long)

}