package me.anno.io

import me.anno.Engine
import me.anno.utils.types.Strings.isBlank2
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

object CommandLineReader {

    class TimeoutReader(val input: InputStream) : Closeable {

        val str = ArrayList<Byte>(256)

        fun readLine(): String? {
            while (!Engine.shutdown) {
                if (isReady()) {
                    when (val char = input.read()) {
                        '\r'.code -> continue // skip it
                        '\n'.code -> {// return line
                            val value = String(str.toByteArray())
                            str.clear()
                            return value
                        }
                        -1 -> break // eof
                        else -> str.add(char.toByte())
                    }
                } else Thread.sleep(1)
            }
            return null
        }

        override fun close() {
            input.close()
        }

        private fun isReady(): Boolean {
            return try {
                this.input.available() > 0
            } catch (var2: IOException) {
                false
            }
        }

    }

    fun start() {
        thread(name = "CommandReader") {
            val input = TimeoutReader(System.`in`)
            while (true) {
                val line = input.readLine() ?: break
                // todo analyse the line content
                if(line.isBlank2()) continue
                println(line)
            }
        }
    }

}