package me.anno.engine

import me.anno.Engine
import me.anno.Time
import me.anno.maths.Maths
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue

/**
 * This is the Android-equivalent of runOnUIThread():
 *  these events will be executed together before rendering each frame
 * */
object Events {

    private class ScheduledTask(val time: Long, val runnable: () -> Unit) : Comparable<ScheduledTask> {
        override fun compareTo(other: ScheduledTask): Int {
            return time.compareTo(other.time)
        }
    }

    private val eventTasks: Queue<() -> Unit> = ConcurrentLinkedQueue()
    private val scheduledTasks: Queue<ScheduledTask> = PriorityBlockingQueue(16)

    /**
     * schedules a task that will be executed on the main loop
     * */
    fun addEvent(event: () -> Unit) {
        eventTasks += event
    }

    /**
     * schedules a task that will be executed on the main loop;
     * will wait at least deltaMillis before it is executed
     * */
    fun addEvent(deltaMillis: Long, event: () -> Unit) {
        if (deltaMillis <= 0) {
            addEvent(event)
        } else {
            scheduledTasks.add(ScheduledTask(Time.nanoTime + deltaMillis * Maths.MILLIS_TO_NANOS, event))
        }
    }

    fun workEventTasks() {
        val time = Time.nanoTime
        while (scheduledTasks.isNotEmpty()) {
            try {
                val peeked = scheduledTasks.peek()!!
                if (time >= peeked.time) {
                    scheduledTasks.poll()!!.runnable()
                } else break
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        while (eventTasks.isNotEmpty()) {
            try {
                eventTasks.poll()!!.invoke()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    init {
        Engine.registerForShutdown {
            finishEventTasks()
        }
    }

    /**
     * if you want this to execute, just properly request shutdown from the Engine
     * */
    private fun finishEventTasks() {
        workEventTasks()
        while (scheduledTasks.isNotEmpty()) {
            try {
                scheduledTasks.poll()!!.runnable()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}