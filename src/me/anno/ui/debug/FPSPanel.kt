package me.anno.ui.debug

import me.anno.gpu.GFX
import me.anno.gpu.GFXx2D
import me.anno.ui.base.Font
import me.anno.utils.FloatFormat.f1
import kotlin.math.max

object FPSPanel {

    var font = Font("Consolas", 12f, false, false)

    fun showFPS() {

        val x0 = max(0, GFX.width - FrameTimes.width)
        val y0 = max(0, GFX.height - FrameTimes.height)
        FrameTimes.place(x0, y0, FrameTimes.width, FrameTimes.height)
        FrameTimes.draw()
        GFX.loadTexturesSync.push(true)
        var x = x0 + 1
        val text = "${GFX.currentEditorFPS.f1()}, min: ${(1f / FrameTimes.maxValue).f1()}"
        val sample = GFXx2D.getTextSize(font, "w", -1)
        val charWidth = sample.first
        GFXx2D.drawRect(x, y0 + 1, charWidth * text.length, sample.second, FrameTimes.backgroundColor)
        for (char in text) {
            GFXx2D.drawText(
                x, y0 + 1,
                font, "$char",
                FrameTimes.textColor,
                FrameTimes.backgroundColor,
                -1
            )
            x += charWidth
        }

        // keep these chars loaded at all times
        for (char in "0123456789.") {
            GFXx2D.getTextSize(font, "$char", -1)
        }

        GFX.loadTexturesSync.pop()

    }

}