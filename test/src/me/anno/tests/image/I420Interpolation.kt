package me.anno.tests.image

import me.anno.gpu.texture.Texture2D
import me.anno.image.ImageCache
import me.anno.utils.OS
import me.anno.video.formats.cpu.I420Frame
import java.nio.ByteBuffer

fun main() {

    fun interpolate(xi: Int, yi: Int, w2: Int, data: ByteBuffer): Int {
        val xf = xi.and(1)
        val yf = yi.and(1)
        val xj = xi shr 1
        val yj = yi shr 1
        val bj = xj + yj * w2
        return when {
            xf == 0 && yf == 0 -> data[bj].toInt().and(255) // no interpolation needed
            yf == 0 -> I420Frame.mix(data[bj], data[bj + 1]) // only x interpolation needed
            xf == 0 -> I420Frame.mix(data[bj], data[bj + w2]) // only y interpolation needed
            else -> {
                val a = data[bj].toInt().and(255)
                val b = data[bj + 1].toInt().and(255)
                val c = data[bj + w2].toInt().and(255)
                val d = data[bj + w2 + 1].toInt().and(255)
                (a + b + c + d).shr(2)
            }
        }
    }

    val runs = 1000

    val w = 512
    val h = 512

    val w2 = (w + 1) / 2
    val h2 = (h + 1) / 2

    val wx = w + w.and(1) - 1 // same if odd, -1 else
    val hx = h + h.and(1) - 1

    val s0 = w * h
    val s1 = w2 * h2

    val data = IntArray(w * h)
    val yData = Texture2D.bufferPool[s0, false, false]
    val uData = Texture2D.bufferPool[s1, false, false]
    val vData = Texture2D.bufferPool[s1, false, false]

    val t0 = System.nanoTime()

    for (i in 0 until runs) {
        for (yi in 0 until hx) {
            for (xi in 0 until wx) {
                val it = xi + w * yi
                data[it] = I420Frame.yuv2rgb(
                    yData[it],
                    interpolate(xi, yi, w2, uData),
                    interpolate(xi, yi, w2, vData)
                )
            }
        }
    }

    val t1 = System.nanoTime()

    for (i in 0 until runs) {
        for (yi in 0 until hx step 2) {
            var it = yi * w
            for (xi in 0 until wx step 2) {
                data[it] = I420Frame.yuv2rgb(
                    yData[it],
                    I420Frame.int00(xi, yi, w2, uData),
                    I420Frame.int00(xi, yi, w2, vData)
                )
                it += 2
            }
            it = 1 + yi * w
            for (xi in 1 until wx step 2) {
                data[it] = I420Frame.yuv2rgb(
                    yData[it],
                    I420Frame.int10(xi, yi, w2, uData),
                    I420Frame.int10(xi, yi, w2, vData)
                )
                it += 2
            }
        }

        for (yi in 1 until hx step 2) {
            var it = yi * w
            for (xi in 0 until wx step 2) {
                data[it] = I420Frame.yuv2rgb(
                    yData[it],
                    I420Frame.int01(xi, yi, w2, uData),
                    I420Frame.int01(xi, yi, w2, vData)
                )
                it += 2
            }
            it = 1 + yi * w
            for (xi in 1 until wx step 2) {
                data[it] = I420Frame.yuv2rgb(
                    yData[it],
                    I420Frame.int11(xi, yi, w2, uData),
                    I420Frame.int11(xi, yi, w2, vData)
                )
                it += 2
            }
        }

    }

    val t2 = System.nanoTime()
    println("${(t1 - t0) / 1e9} vs ${(t2 - t1) / 1e9}")

    ImageCache[OS.pictures.getChild("Anime/70697252_p4_master1200.webp"), false]!!
        .write(OS.desktop.getChild("anime.png"))
}