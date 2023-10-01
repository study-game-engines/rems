package me.anno.ui.editor.color

import me.anno.studio.StudioBase.Companion.dragged
import me.anno.ui.Panel
import me.anno.ui.Style
import org.joml.Vector3f

open class HSVBox(
    val chooser: ColorChooser,
    val v0: Vector3f,
    val du: Vector3f,
    val dv: Vector3f,
    val dh: Float,
    style: Style,
    size: Float,
    val onValueChanged: (Float, Float) -> Unit
) : Panel(style) {

    val minH1 = (size * style.getSize("fontSize", 14)).toInt()

    override fun calculateSize(w: Int, h: Int) {
        minH = minH1
        minW = w
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        chooser.drawColorBox(this, v0, du, dv, dh, false)
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "SelectColor" -> {
                if (dragged == null) {
                    val width = lx1 - lx0 // width itself is somehow incorrect...
                    onValueChanged((x - this.x) / width, 1f - (y - this.y) / height)
                    return true
                }
            }
        }
        return super.onGotAction(x, y, dx, dy, action, isContinuous)
    }

    override val className: String get() = "HSVBox"
}