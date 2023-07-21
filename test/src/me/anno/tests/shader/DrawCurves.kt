package me.anno.tests.shader

import me.anno.config.DefaultConfig.style
import me.anno.gpu.drawing.DrawCurves.drawCubicBezier
import me.anno.gpu.drawing.DrawCurves.drawLine
import me.anno.gpu.drawing.DrawCurves.drawQuadraticBezier
import me.anno.gpu.drawing.DrawCurves.drawQuartBezier
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.input.MouseButton
import me.anno.maths.Maths.TAUf
import me.anno.maths.Maths.sq
import me.anno.ui.base.groups.MapPanel
import me.anno.ui.debug.TestStudio.Companion.testUI3
import me.anno.utils.Color.black
import org.joml.Vector2f
import kotlin.math.cos
import kotlin.math.sin

fun main() {
    val np = 14
    val global = Array(np) {
        val a = it * TAUf / np
        Vector2f(cos(a), sin(a)).mul(300f)
    }
    val local = Array(np) { Vector2f() }
    testUI3("Draw Curves") {
        object : MapPanel(style) {

            var selected: Vector2f? = null

            init {
                minScale = 0.01
                maxScale = 100.0
            }

            override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
                super.onDraw(x0, y0, x1, y1)

                val c0 = -1
                val c1 = 0x777777 or black
                val c2 = 0xff0000 or black
                val c3 = 0xffff00 or black
                val bg = backgroundColor and 0xffffff

                val r = 3f * scale.toFloat()
                val th = 7f * scale.toFloat()

                for (i in 0 until np) {
                    val g = global[i]
                    local[i].set(coordsToWindowX(g.x.toDouble()), coordsToWindowY(g.y.toDouble()))
                }

                drawRect(local[1].x, local[1].y, r, r, c3)
                drawRect(local[2].x, local[2].y, r, r, c3)
                drawRect(local[3].x, local[3].y, r, r, c3)

                drawRect(local[6].x, local[6].y, r, r, c0)
                drawRect(local[7].x, local[7].y, r, r, c0)

                drawRect(local[10].x, local[10].y, r, r, c1)

                drawQuartBezier(
                    local[0].x, local[0].y,
                    local[1].x, local[1].y,
                    local[2].x, local[2].y,
                    local[3].x, local[3].y,
                    local[4].x, local[4].y,
                    th, c3, bg,
                    false
                )
                drawCubicBezier(
                    local[5].x, local[5].y,
                    local[6].x, local[6].y,
                    local[7].x, local[7].y,
                    local[8].x, local[8].y,
                    th, c0, bg,
                    false
                )
                drawQuadraticBezier(
                    local[9].x, local[9].y,
                    local[10].x, local[10].y,
                    local[11].x, local[11].y,
                    th, c1, bg, false
                )
                drawLine(
                    local[12].x, local[12].y,
                    local[13].x, local[13].y,
                    th, c2, bg, false
                )
            }

            override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
                if (button.isLeft) {
                    val lx = windowToCoordsX(x)
                    val ly = windowToCoordsY(y)
                    val maxDistSq = sq(10f / scale.toFloat())
                    selected = global.withIndex()
                        .filter { it.value.distanceSquared(lx, ly) < maxDistSq }
                        .minByOrNull { it.value.distanceSquared(lx, ly) }?.value
                    if (selected == null) super.onMouseDown(x, y, button)
                } else super.onMouseDown(x, y, button)
            }

            override fun onMouseUp(x: Float, y: Float, button: MouseButton) {
                if (button.isLeft && selected != null) {
                    selected = null
                } else super.onMouseUp(x, y, button)
            }

            override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
                val selected = selected
                if (selected != null && (dx != 0f || dy != 0f)) {
                    val scale = 1f / scale.toFloat()
                    selected.add(dx * scale, dy * scale)
                    invalidateDrawing()
                } else super.onMouseMoved(x, y, dx, dy)
            }
        }
    }
}