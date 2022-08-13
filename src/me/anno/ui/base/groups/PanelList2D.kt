package me.anno.ui.base.groups

import me.anno.Engine
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.input.MouseButton
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.fract
import me.anno.maths.Maths.mix
import me.anno.ui.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.scrolling.ScrollPanelXY
import me.anno.ui.base.scrolling.ScrollPanelXY.Companion.scrollSpeed
import me.anno.ui.base.scrolling.ScrollableY
import me.anno.ui.base.scrolling.ScrollbarY
import me.anno.ui.style.Style
import kotlin.math.max
import kotlin.math.min

class PanelList2D(sorter: Comparator<Panel>?, style: Style) : PanelList(sorter, style), ScrollableY {

    constructor(style: Style) : this(null, style)

    constructor(base: PanelList2D) : this(base.sorter, base.style) {
        base.copy(this)
    }

    override val children = ArrayList<Panel>(256)
    override val child: Panel
        get() = this

    // different modes for left/right alignment
    var childAlignmentX = AxisAlignment.CENTER

    val defaultSize = 100
    var scaleChildren = false

    var childWidth: Int = style.getSize("childWidth", defaultSize)
        set(value) {
            if (field != value) {
                field = value
                invalidateLayout()
            }
        }

    var childHeight: Int = style.getSize("childHeight", defaultSize)
        set(value) {
            if (field != value) {
                field = value
                invalidateLayout()
            }
        }

    override fun invalidateLayout() {
        window?.needsLayout?.add(this)
    }

    var rows = 1
    var columns = 1
    var calcChildWidth = 0
    var calcChildHeight = 0
    var minH2 = 0

    override var scrollPositionY = 0.0
    var isDownOnScrollbar = false

    override val maxScrollPositionY get(): Long = max(0, minH2 - h).toLong()

    override fun scrollY(delta: Double) {
        scrollPositionY += delta
        clampScrollPosition()
        invalidateLayout()
    }

    val scrollbar = ScrollbarY(this, style)
    var scrollbarWidth = style.getSize("scrollbarWidth", 8)
    var scrollbarPadding = style.getSize("scrollbarPadding", 1)

    override fun calculateSize(w: Int, h: Int) {

        val children = children
        if (sorter != null) {
            children.sortWith(sorter)
        }

        val w2 = min(w, childWidth * children.size)

        updateSize(w2)

        // only execute for visible children
        for (i in visibleIndex0 until visibleIndex1) {
            val child = children[i]
            if (child.visibility != Visibility.GONE) {
                child.calculateSize(calcChildWidth, calcChildHeight)
            }
        }

    }

    override fun drawChildren(x0: Int, y0: Int, x1: Int, y1: Int) {
        // todo optimize this for PanelListX and PanelListY
        val children = children
        for (index in visibleIndex0 until visibleIndex1) {
            val child = children[index]
            if (child.visibility == Visibility.VISIBLE) {
                drawChild(child, x0, y0, x1, y1)
            }
        }
    }

    val visibleIndex0
        get() = max((ly0 - (y + spacing - scrollPositionY.toInt())) / childHeight, 0) * columns
    val visibleIndex1
        get() = min(
            (ly1 - (y + spacing - scrollPositionY.toInt()) + childHeight - 1) / childHeight * columns,
            children.size
        )

    override fun forAllVisiblePanels(callback: (Panel) -> Unit) {
        if (canBeSeen) {
            callback(this)
            val children = children
            // only execute for visible children
            // todo optimize this for PanelListX and PanelListY
            for (i in visibleIndex0 until visibleIndex1) {
                val child = children[i]
                child.parent = this
                child.forAllVisiblePanels(callback)
            }
        }
    }


    var autoScrollTargetPosition = 0.0
    var autoScrollEndTime = 0L
    var autoScrollPerNano = 1f
    var autoScrollLastUpdate = 0L

    fun scrollTo(itemIndex: Int, fractionY: Float) {
        val child = children.getOrNull(itemIndex) ?: return
        val currentY = child.y + fractionY * child.h
        val targetY = windowStack.mouseY
        val newScrollPosition = scrollPositionY + (currentY - targetY)
        smoothlyScrollTo(newScrollPosition, 0.25f)
    }

    override fun onUpdate() {
        super.onUpdate()
        val window = window
        if (window != null) {
            val mx = window.mouseXi
            val my = window.mouseYi
            scrollbar.isBeingHovered = capturesChildEvents(mx, my)
            if (scrollbar.updateAlpha()) invalidateDrawing()
        }
        if (autoScrollLastUpdate < autoScrollEndTime) {
            val delta = autoScrollPerNano * (Engine.gameTime - autoScrollLastUpdate)
            if (delta > 0L) {
                scrollPositionY = if (autoScrollLastUpdate < autoScrollEndTime && delta < 1f) {
                    mix(scrollPositionY, autoScrollTargetPosition, delta.toDouble())
                } else autoScrollTargetPosition
                clampScrollPosition()
                invalidateLayout()
                autoScrollLastUpdate = Engine.gameTime
            }
        }
    }

    val interactionWidth = scrollbarWidth + 2 * interactionPadding
    val hasScrollbar get() = maxScrollPositionY > 0

    override fun capturesChildEvents(lx0: Int, ly0: Int, lx1: Int, ly1: Int): Boolean {
        val sbWidth = interactionWidth + 2 * scrollbarPadding
        return hasScrollbar && ScrollPanelXY.drawsOverY(
            this.lx0, this.ly0, this.lx1, this.ly1,
            sbWidth, lx0, ly0, lx1, ly1
        )
    }

    fun smoothlyScrollTo(y: Double, duration: Float = 1f) {
        autoScrollTargetPosition = clamp(y, 0.0, maxScrollPositionY.toDouble())
        autoScrollPerNano = 5e-9f / duration
        autoScrollEndTime = Engine.gameTime + (duration * 1e9f).toLong()
        autoScrollLastUpdate = Engine.gameTime
        if (duration <= 0f) scrollPositionY = autoScrollTargetPosition
        invalidateDrawing()
    }

    fun getItemIndexAt(x: Float, y: Float): Int {
        val localX = x - this.x
        val localY = y - this.y + scrollPositionY - spacing
        val itemX = (localX * columns / w).toInt()
        val itemY = (localY * rows / h).toInt()
        return clamp(itemX + itemY * columns, 0, children.lastIndex)
    }

    fun getItemFractionY(y: Float): Double {
        val ly = y - this.y + scrollPositionY - spacing
        val itemY = ly * rows / h
        return fract(itemY)
    }

    private fun updateCount() {
        columns = max(1, (w + spacing) / (childWidth + spacing))
        rows = max(1, (children.size + columns - 1) / columns)
    }

    private fun updateScale() {
        val childScale = if (scaleChildren) max(1f, ((w + spacing) / columns - spacing) * 1f / childWidth) else 1f
        calcChildWidth = if (scaleChildren) (childWidth * childScale).toInt() else childWidth
        calcChildHeight = if (scaleChildren) (childHeight * childScale).toInt() else childHeight
    }

    private fun updateSize(w: Int) {
        updateCount()
        updateScale()
        minW = min(w, children.size * (calcChildWidth + spacing) + spacing)
        minH = (calcChildHeight + spacing) * rows - spacing
        minH2 = minH
    }

    override fun setSize(w: Int, h: Int) {
        updateSize(w)
        super.setSize(w, h)
    }

    override fun setPosition(x: Int, y: Int) {
        super.setPosition(x, y)

        val w = w - scrollbarWidth
        val contentW = columns * childWidth

        val scroll = scrollPositionY.toInt()

        // only place visible children + all that were previously visible
        val vi0 = visibleIndex0
        val vi1 = visibleIndex1
        val idx0 = max(min(vi0, lpi0), 0)
        val idx1 = min(max(vi1, lpi1), children.size)
        lpi0 = vi0
        lpi1 = vi1

        for (i in idx0 until idx1) {
            val child = children[i]
            if (child.visibility != Visibility.GONE) {
                val ix = i % columns
                val iy = i / columns
                val cx = x + when (childAlignmentX) {
                    AxisAlignment.MIN, AxisAlignment.FILL -> ix * (calcChildWidth + spacing) + spacing
                    AxisAlignment.CENTER -> ix * calcChildWidth + max(0, w - contentW) * (ix + 1) / (columns + 1)
                    AxisAlignment.MAX -> w - (columns - ix) * (calcChildWidth + spacing)
                }
                val cy = y + iy * (calcChildHeight + spacing) + spacing - scroll
                // child.placeInParent(cx, cy)
                child.setPosSize(cx, cy, calcChildWidth, calcChildHeight)
            }
        }

    }

    private var lpi0 = 0
    private var lpi1 = Int.MAX_VALUE

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        clampScrollPosition()
        if (hasScrollbar) {
            scrollbar.x = x1 - scrollbarWidth - scrollbarPadding
            scrollbar.y = y + scrollbarPadding
            scrollbar.w = scrollbarWidth
            scrollbar.h = h - 2 * scrollbarPadding
            drawChild(scrollbar, x0, y0, x1, y1)
        }
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float, byMouse: Boolean) {
        val delta = -dy * scrollSpeed
        if ((delta > 0f && scrollPositionY >= maxScrollPositionY) ||
            (delta < 0f && scrollPositionY <= 0f)
        ) {// if done scrolling go up the hierarchy one
            super.onMouseWheel(x, y, dx, dy, byMouse)
        } else {
            scrollPositionY += delta
            clampScrollPosition()
            invalidateLayout()
            // we consumed dy
            if (dx != 0f) {
                super.onMouseWheel(x, y, dx, 0f, byMouse)
            }
        }
    }

    override fun updateChildrenVisibility(mx: Int, my: Int) {

        val vi0 = visibleIndex0
        val vi1 = visibleIndex1
        val idx0 = max(min(vi0, lpi2), 0)
        val idx1 = min(max(vi1, lpi3), children.size)
        lpi2 = vi0
        lpi3 = vi1

        val children = children
        for (i in idx0 until idx1) {
            children[i].updateVisibility(mx, my)
        }
    }

    private var lpi2 = 0
    private var lpi3 = Int.MAX_VALUE

    private fun clampScrollPosition() {
        scrollPositionY = clamp(scrollPositionY, 0.0, maxScrollPositionY.toDouble())
    }

    override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
        isDownOnScrollbar = scrollbar.contains(x, y, scrollbarPadding * 2)
        if (!isDownOnScrollbar) super.onMouseDown(x, y, button)
    }

    override fun onMouseUp(x: Float, y: Float, button: MouseButton) {
        isDownOnScrollbar = false
        super.onMouseUp(x, y, button)
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (isDownOnScrollbar) {
            scrollbar.onMouseMoved(x, y, dx, dy)
            clampScrollPosition()
        } else super.onMouseMoved(x, y, dx, dy)
    }

    override fun clone() = PanelList2D(this)

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as PanelList2D
        clone.childWidth = childWidth
        clone.childHeight = childHeight
        clone.scaleChildren = scaleChildren
        clone.childAlignmentX = childAlignmentX
        clone.rows = rows
        clone.columns = columns
        clone.spacing = spacing
        clone.scrollPositionY = scrollPositionY
        clone.isDownOnScrollbar = isDownOnScrollbar
        clone.scrollbarWidth = scrollbarWidth
        clone.scrollbarPadding = scrollbarPadding
        clone.autoScrollEndTime = autoScrollEndTime
        clone.autoScrollPerNano = autoScrollPerNano
        clone.autoScrollLastUpdate = autoScrollLastUpdate
        clone.autoScrollTargetPosition = autoScrollTargetPosition
    }

}