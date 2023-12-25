package me.anno

import me.anno.ui.debug.FrameTimings
import kotlin.math.min

/**
 * state of time, for UI, and timelapse-able elements, and nanoTime (everything)
 * */
object Time {

    /**
     * game time since last frame in seconds;
     * 0.1 at max.
     * */
    @JvmField
    var deltaTime = 0.0

    /**
     * game time since last frame in seconds;
     * not clamped
     * */
    @JvmField
    var rawDeltaTime = 0.0

    /**
     * estimate for current fps
     * */
    @JvmField
    var currentFPS = 60.0

    /**
     * nanoTime of when the engine was started in OS time
     * */
    @JvmField
    val startTime = System.nanoTime()

    @JvmStatic
    var lastTimeNanos = startTime
        private set

    /**
     * dateTime of when the engine was started
     * */
    @JvmField
    val startDateTime = System.currentTimeMillis()

    /**
     * time at this moment since the engine started; in nanoseconds;
     * should be used for UI, and animations that shouldn't be scaled in time
     * */
    @JvmStatic
    val nanoTime get(): Long = System.nanoTime() - startTime

    /**
     * time of current frame; integrated by time speed; in nanoseconds
     * use gameTime for a float value
     *
     * This is used for Transform.lastDrawn, Transform.getDrawMatrix()
     *
     * todo this shouldn't be used by UI, use nanoTime instead
     * */
    @JvmStatic
    var gameTimeN: Long = 0L
        private set

    /**
     * time of current frame; integrated by time speed; in nanoseconds
     * use gameTimeN for a long value
     * */
    @JvmStatic
    var gameTime: Double = 0.0
        private set

    @JvmStatic
    var lastGameTime: Long = 0L
        private set

    @JvmStatic
    var frameIndex: Int = 0
        private set

    /**
     * how fast gameTime is increased relative to actual nanoTime;
     * most gameplay should use gameTime, most UI should use nanoTime;
     *
     * when you accelerate parts of your game, or slow them down, everything should (^^) scale accordingly
     * */
    @JvmStatic
    var timeSpeed: Double = 1.0

    @JvmStatic
    fun updateTime() {
        val thisTime = nanoTime
        val rawDeltaTime = (thisTime - lastTimeNanos) * 1e-9
        updateTime(rawDeltaTime, thisTime)
    }

    @JvmStatic
    fun updateTime(dt: Double, thisTime: Long) {

        rawDeltaTime = dt
        deltaTime = min(rawDeltaTime, 0.1)
        FrameTimings.putTime(rawDeltaTime.toFloat())

        val newFPS = 1.0 / rawDeltaTime
        currentFPS = min(currentFPS + (newFPS - currentFPS) * 0.05, newFPS)
        lastTimeNanos = thisTime

        lastGameTime = gameTimeN
        gameTimeN += (dt * timeSpeed * 1e9).toLong()
        gameTime = gameTimeN * 1e-9

        frameIndex++
    }
}