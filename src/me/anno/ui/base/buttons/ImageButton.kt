package me.anno.ui.base.buttons

import me.anno.config.DefaultStyle.black
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.gpu.OpenGL.renderDefault
import me.anno.gpu.drawing.DrawTextures
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.image.ImageGPUCache.getInternalTexture
import me.anno.input.Input.mouseDownX
import me.anno.input.Input.mouseDownY
import me.anno.input.Input.mouseKeysDown
import me.anno.ui.Panel
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.style.Style
import kotlin.math.max
import kotlin.math.roundToInt

// todo scale image down with subpixel rendering?

class ImageButton(
    var path: String,
    var size: Int,
    var padding: Padding,
    var isSquare: Boolean = true,
    style: Style
) : Panel(style) {

    constructor(style: Style) : this("", 16, Padding(5), true, style)

    // private val path = //if (imageName.startsWith("textures/")) imageName else "textures/ui/$imageName"

    var guiScale = 1f

    private val icon get() = getInternalTexture(path, true, 10_000)

    init {
        add(WrapAlign.LeftTop)
    }

    override fun getVisualState(): Any? = icon
    override fun getLayoutState(): Any? = icon?.w

    override fun calculateSize(w: Int, h: Int) {
        val icon = icon ?: return
        minW = ((size + padding.width) * guiScale).toInt()
        minH = if (isSquare) ((size) * guiScale + padding.height).toInt() else {
            ((icon.h * size / icon.w) * guiScale + padding.height).toInt()
        }
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        val tint = when {
            0 in mouseKeysDown && contains(mouseDownX, mouseDownY) -> black or 0x777777
            isHovered -> black or 0xaaaaaa
            else -> -1
        }
        drawBackground(x0, y0, x1, y1)
        val icon = icon ?: return
        renderDefault {
            icon.bind(0, GPUFiltering.LINEAR, Clamping.CLAMP)
            val scale = ((w - padding.width).toFloat() / max(icon.w, icon.h))
            val iw = (icon.w * scale).roundToInt()
            val ih = (icon.h * scale).roundToInt()
            DrawTextures.drawTexture(x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, icon, tint, null)
        }
    }

    override fun clone(): ImageButton {
        val clone = ImageButton(style)
        copy(clone)
        return clone
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as ImageButton
        clone.path = path
        clone.padding = padding
        clone.size = size
        clone.isSquare = isSquare
    }

    override val className: String = "ImageButton"

}