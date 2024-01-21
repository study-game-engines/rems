package org.apache.logging.log4j

import me.anno.Time
import me.anno.maths.Maths.MILLIS_TO_NANOS
import org.apache.commons.logging.Log
import java.util.Calendar
import java.util.Locale
import java.util.logging.Level

open class LoggerImpl(val prefix: String?) : Logger, Log {

    private val lastWarned = HashMap<String, Long>()
    private val warningTimeoutNanos = 10e9.toLong()

    private fun interleave(msg: String, args: Array<out Any?>): String {
        if (args.isEmpty()) return msg
        return if (msg.contains("{}")) {
            val builder = StringBuilder(msg.length)
            var i = 0
            var j = 0
            while (i < msg.length) {
                val li = i
                i = msg.indexOf("{}", i)
                if (i < 0 || j >= args.size) {
                    builder.append(msg, li, msg.length)
                    break
                } else {
                    builder.append(msg, li, i)
                    builder.append(args[j++])
                    i += 2 // skip over {}
                }
            }
            builder.toString()
        } else {
            msg.format(Locale.ENGLISH, *args)
            /*val funFormat = String::class.java.getMethod("format", Locale::class.java, Array<Object>(0){ throw RuntimeException() }::class.java)
            return funFormat.invoke(msg, Locale.ENGLISH, args) as String
            return msg.format(Locale.ENGLISH, args)*/
        }
    }

    private fun interleave(msg: String, arg0: Any?): String {
        val index = msg.indexOf("{}")
        return if (index >= 0) {
            val arg = arg0.toString()
            val builder = StringBuilder(msg.length + arg.length)
            builder.append(msg, 0, index)
            builder.append(arg)
            builder.append(msg, index + 2, msg.length)
            builder.toString()
        } else msg
    }

    private val suffix = if (prefix == null) "" else ":$prefix"

    fun print(prefix: String, msg: String) {
        for (line in msg.split('\n')) {
            val line2 = "[${getTimeStamp()},$prefix$suffix] $line"
            if (prefix == "ERR!" || prefix == "WARN") {
                System.err.println(line2)
            } else {
                println(line2)
            }
        }
    }

    override fun info(msg: String) {
        if (isInfoEnabled()) {
            print("INFO", msg)
        }
    }

    override fun info(msg: String, vararg obj: Any?) {
        if (isInfoEnabled()) {
            info(interleave(msg, obj))
        }
    }

    override fun info(marker: Marker, msg: String) {
        info(msg)
    }

    override fun info(msg: String, thrown: Throwable) {
        if (isInfoEnabled()) {
            info(msg)
            thrown.printStackTrace()
        }
    }

    override fun debug(msg: String) {
        if (isDebugEnabled()) {
            print("DEBUG", msg)
        }
    }

    override fun debug(msg: String, e: Throwable) {
        if (isDebugEnabled()) {
            print("DEBUG", msg)
            e.printStackTrace()
        }
    }

    override fun debug(o: Any?) {
        if (isDebugEnabled()) {
            debug(o.toString())
        }
    }

    override fun debug(o: Any?, throwable: Throwable?) {
        if (isDebugEnabled()) {
            if (throwable == null) debug(o)
            else debug(o.toString(), throwable)
        }
    }

    open fun debug(msg: String, obj: Any?) {
        if (isDebugEnabled()) {
            debug(interleave(msg, obj))
        }
    }

    override fun debug(msg: String, vararg obj: Any?) {
        if (isDebugEnabled()) {
            debug(interleave(msg, obj))
        }
    }

    override fun error(msg: String) {
        if (isErrorEnabled()) {
            print("ERR!", msg)
        }
    }

    override fun error(msg: String, vararg obj: Any?) {
        if (isErrorEnabled()) {
            error(interleave(msg, obj))
        }
    }

    override fun error(msg: String, thrown: Throwable) {
        if (isErrorEnabled()) {
            error(msg)
            thrown.printStackTrace()
        }
    }

    override fun error(o: Any?) {
        if (isErrorEnabled()) {
            error(o.toString())
        }
    }

    override fun error(o: Any?, throwable: Throwable?) {
        if (isErrorEnabled()) {
            if (throwable == null) error(o)
            else error(o.toString(), throwable)
        }
    }

    override fun severe(msg: String) {
        if (isSevereEnabled()) {
            print("SEVERE", msg)
        }
    }

    override fun severe(msg: String, vararg obj: Any?) {
        if (isSevereEnabled()) {
            error(interleave(msg, obj))
        }
    }

    override fun severe(msg: String, thrown: Throwable) {
        if (isSevereEnabled()) {
            error(msg)
            thrown.printStackTrace()
        }
    }

    override fun fatal(msg: String) {
        if (isFatalEnabled()) {
            print("FATAL", msg)
        }
    }

    override fun fatal(msg: String, vararg obj: Any?) {
        if (isFatalEnabled()) {
            fatal(interleave(msg, obj))
        }
    }

    override fun fatal(msg: String, thrown: Throwable) {
        if (isFatalEnabled()) {
            fatal(msg)
            thrown.printStackTrace()
        }
    }

    override fun warn(msg: String) {
        if (isWarnEnabled()) {
            synchronized(lastWarned) {
                val time = Time.nanoTime
                if (msg !in lastWarned || (lastWarned[msg]!! - time) > warningTimeoutNanos) {
                    lastWarned[msg] = time
                    print("WARN", msg)
                }
            }
        }
    }

    override fun warn(msg: String, vararg obj: Any?) {
        if (isWarnEnabled()) {
            warn(interleave(msg, obj))
        }
    }

    override fun warn(msg: String, thrown: Throwable) {
        if (isWarnEnabled()) {
            warn(msg)
            thrown.printStackTrace()
        }
    }

    override fun warn(o: Any?) {
        if (isWarnEnabled()) {
            if (o is Throwable) {
                @Suppress("KotlinPlaceholderCountMatchesArgumentCount")
                warn("", o)
            } else {
                warn(o.toString())
            }
        }
    }

    override fun warn(o: Any?, throwable: Throwable?) {
        if (isWarnEnabled()) {
            if (throwable == null) warn(o.toString())
            else warn(o.toString(), throwable)
        }
    }

    override fun fatal(o: Any?) {
        if (isFatalEnabled()) {
            fatal(o.toString())
        }
    }

    override fun fatal(o: Any?, throwable: Throwable?) {
        if (isFatalEnabled()) {
            if (throwable == null) fatal(o.toString())
            else fatal(o.toString(), throwable)
        }
    }

    override fun info(msg: Any?) {
        if (isInfoEnabled()) {
            info(msg.toString())
        }
    }

    override fun info(o: Any?, throwable: Throwable?) {
        if (isInfoEnabled()) {
            if (throwable == null) info(o)
            else info(o.toString(), throwable)
        }
    }

    override fun trace(o: Any?) {
        error(o)
    }

    override fun trace(o: Any?, throwable: Throwable?) {
        error(o, throwable)
    }

    override fun isLoggable(level: Level): Boolean {
        return LogManager.isEnabled(this, level.intValue())
    }

    override fun isTraceEnabled(): Boolean {
        return LogManager.isEnabled(this, org.apache.logging.log4j.Level.TRACE)
    }

    override fun isDebugEnabled(): Boolean {
        return LogManager.isEnabled(this, org.apache.logging.log4j.Level.DEBUG)
    }

    override fun isInfoEnabled(): Boolean {
        return LogManager.isEnabled(this, org.apache.logging.log4j.Level.INFO)
    }

    override fun isWarnEnabled(): Boolean {
        return LogManager.isEnabled(this, org.apache.logging.log4j.Level.WARN)
    }

    override fun isFatalEnabled(): Boolean {
        return LogManager.isEnabled(this, org.apache.logging.log4j.Level.FATAL)
    }

    override fun isErrorEnabled(): Boolean {
        return LogManager.isEnabled(this, org.apache.logging.log4j.Level.ERROR)
    }

    fun isSevereEnabled(): Boolean {
        return LogManager.isEnabled(this, org.apache.logging.log4j.Level.SEVERE)
    }

    // override fun warn(marker: Marker, msg: String, vararg obj: java.lang.Object): Unit = warn(msg, obj)
    companion object {
        private var lastTime = 0L
        private var lastString = ""
        fun getTimeStamp(): String {
            val updateInterval = 500 * MILLIS_TO_NANOS
            val time = Time.nanoTime / updateInterval
            synchronized(Unit) {
                if (time == lastTime && lastString.isNotEmpty())
                    return lastString
                val calendar = Calendar.getInstance()
                val seconds = calendar.get(Calendar.SECOND)
                val minutes = calendar.get(Calendar.MINUTE)
                val hours = calendar.get(Calendar.HOUR_OF_DAY)
                lastTime = time
                lastString = "%2d:%2d:%2d".format(hours, minutes, seconds)
                    .replace(' ', '0')
                return lastString
            }
        }
    }
}