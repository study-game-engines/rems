package me.anno.ui.editor.treeView

import me.anno.config.DefaultStyle.midGray
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.fonts.keys.TextCacheKey
import me.anno.gpu.Cursor
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.input.Input
import me.anno.input.MouseButton
import me.anno.io.files.FileReference
import me.anno.io.text.TextReader
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mixARGB
import me.anno.maths.Maths.sq
import me.anno.studio.StudioBase
import me.anno.studio.StudioBase.Companion.dragged
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.menu.Menu.askName
import me.anno.ui.base.text.TextPanel
import me.anno.ui.dragging.Draggable
import me.anno.ui.editor.files.FileContentImporter
import me.anno.ui.style.Style
import me.anno.utils.Color.b
import me.anno.utils.Color.black
import me.anno.utils.Color.g
import me.anno.utils.Color.r
import me.anno.utils.Color.white
import org.apache.logging.log4j.LogManager

class TreeViewPanel<V : Any>(
    val getElement: () -> V,
    val isValidElement: (Any?) -> Boolean,
    val toggleCollapsed: (V) -> Unit,
    val moveChange: (run: () -> Unit) -> Unit,
    val getName: (V) -> String,
    val setName: (V, String) -> Unit,
    val openAddMenu: (parent: V) -> Unit,
    private val fileContentImporter: FileContentImporter<V>,
    showSymbol: Boolean,
    private val treeView: TreeView<V>, style: Style
) : PanelListX(style) {

    companion object {
        @JvmStatic
        private val LOGGER = LogManager.getLogger(TreeViewPanel::class)
    }

    val uiSymbol: TextPanel? = if (showSymbol) {
        object : TextPanel("", style) {

            init {
                textAlignment = AxisAlignment.CENTER
            }

            override fun calculateSize(w: Int, h: Int) {
                val font = font
                minW = font.sampleWidth * 2 + padding.width
                minH = font.sampleHeight + padding.height
                if (text != textCacheKey.text || font.isBold != textCacheKey.isBold() || font.isItalic != textCacheKey.isItalic()) {
                    textCacheKey = TextCacheKey(text, font)
                }
            }

            override fun onCopyRequested(x: Float, y: Float) =
                this@TreeViewPanel.onCopyRequested(x, y)
        }
    } else null

    val text = object : TextPanel("", style) {
        override fun onCopyRequested(x: Float, y: Float) =
            this@TreeViewPanel.onCopyRequested(x, y)
    }

    init {
        if (uiSymbol != null) {
            uiSymbol.enableHoverColor = true
            this += uiSymbol
        }
        text.enableHoverColor = true
        this += text
    }

    fun setText(symbol: String, name: String) {
        this.uiSymbol?.text = symbol
        this.text.text = name
    }

    var showAddIndex: Int? = null

    var textColor
        get() = (uiSymbol ?: text).textColor
        set(value) {
            uiSymbol?.textColor = value
            text.textColor = value
            text.focusTextColor = value
        }

    val font get() = (uiSymbol ?: text).font

    override fun onUpdate() {
        super.onUpdate()
        val transform = getElement()
        val dragged = dragged
        var backgroundColor = originalBGColor
        val window = window!!
        val showAddIndex = if (
            window.mouseXi in lx0..lx1 &&
            window.mouseYi in ly0..ly1 &&
            dragged is Draggable && isValidElement(dragged.getOriginal())
        ) {
            clamp(((window.mouseY - this.y) / this.h * 3).toInt(), 0, 2)
        } else null
        if (this.showAddIndex != showAddIndex) invalidateDrawing()
        this.showAddIndex = showAddIndex
        val isInFocus = isAnyChildInFocus || StudioBase.instance?.isSelected(transform) == true
        val textColor = mixARGB(black, treeView.getLocalColor(transform, isHovered, isInFocus), 180f / 255f)
        val colorDifference = sq(textColor.r() - backgroundColor.r()) +
                sq(textColor.g() - backgroundColor.g()) +
                sq(textColor.b() - backgroundColor.b())
        backgroundColor = if (colorDifference < 512) {// too similar colors
            if (textColor.g() > 127) black
            else white
        } else originalBGColor
        this.textColor = textColor
        text.backgroundColor = backgroundColor
        uiSymbol?.backgroundColor = backgroundColor
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        // draw the paste-preview
        val showAddIndex = showAddIndex
        if (showAddIndex != null) {
            val x = x + padding.left
            val textSize = font.sizeInt
            val indent = textSize + 0
            val lineWidth = textSize * 7
            val lineColor = midGray
            when (showAddIndex) {
                0 -> drawRect(x, y, lineWidth, 1, lineColor)
                1 -> drawRect(x + indent, y + h - 1, lineWidth, 1, lineColor)
                2 -> drawRect(x, y + h - 1, lineWidth, 1, lineColor)
            }
        }
    }

    private fun isMouseOnSymbol(x: Float): Boolean {
        return uiSymbol != null && x <= uiSymbol.lx1
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        val element = getElement()
        when {
            button.isLeft -> {
                // collapse, if you click on the symbol
                // todo selecting multiple isn't working yet :/
                val inFocusByParent = siblings.count { it is TreeViewPanel<*> && it.isAnyChildInFocus }
                LOGGER.debug(
                    "[TVP] click -> ${siblings.size}, ${siblings.count { it is TreeViewPanel<*> }}, $inFocusByParent, " +
                            "${Input.isShiftDown}, ${isMouseOnSymbol(x)}"
                )
                if (Input.isShiftDown && inFocusByParent < 2) {
                    toggleCollapsed(element)
                    uiParent?.invalidateLayout()
                } else if (isMouseOnSymbol(x)) {
                    toggleCollapsed(element)
                    uiParent?.invalidateLayout()
                } else {
                    val elements = siblings.mapNotNull {
                        if (it == this) element
                        else if (it is TreeViewPanel<*> && it.isAnyChildInFocus)
                            it.getElement() as V
                        else null
                    }
                    treeView.selectElementsMaybe(elements)
                }
            }
            button.isRight -> openAddMenu(element)
        }
    }

    override fun onDoubleClick(x: Float, y: Float, button: MouseButton) {
        when {
            button.isLeft -> treeView.focusOnElement(getElement())
            // button.isRight -> toggleCollapsed()
            else -> super.onDoubleClick(x, y, button)
        }
    }

    override fun onCopyRequested(x: Float, y: Float): String {
        return treeView.stringifyForCopy(getElement())
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        val hovered = getElement()
        if (hovered is PrefabSaveable && hovered.root.prefab?.isWritable == false) {
            LOGGER.warn("Prefab is not writable!")
        } else try {

            val relativeY = (y - this.y) / this.h

            // todo find type somehow...
            val type1 = ' '

            // check if the element can be moved without deleting everything
            @Suppress("unchecked_cast")
            val original = (dragged as? Draggable)?.getOriginal() as? V
            if (original != null) {
                var canBeMoved = true
                var ancestor = hovered
                while (true) {
                    if (original === ancestor) {
                        canBeMoved = false
                        break
                    }
                    ancestor = ancestor.parent ?: break
                }
                if (canBeMoved) {
                    moveChange {
                        val parent = treeView.getParent(original)
                        if (parent != null) treeView.removeChild(parent, original)
                        insertElement(relativeY, hovered, original, type1)
                    }
                    return
                }
            }

            // if not, create a copy
            @Suppress("unchecked_cast")
            val clone = TextReader.read(data, StudioBase.workspace, true).firstOrNull()
                    as? V ?: return super.onPaste(x, y, data, type)

            moveChange {
                insertElement(relativeY, hovered, clone, type1)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            super.onPaste(x, y, data, type)
        }
    }

    fun insertElement(relativeY: Float, hovered: V, clone: V, type: Char) {
        val success = if (relativeY < 0.33f) {
            // paste on top
            if (hovered.parent != null) {
                treeView.addBefore(hovered, clone, type)
            } else {
                insertElementLast(hovered, clone, type)
            }
        } else if (relativeY < 0.67f) {
            // paste as child
            insertElementLast(hovered, clone, type)
        } else {
            // paste below
            if (hovered.parent != null) {
                treeView.addAfter(hovered, clone, type)
            } else {
                insertElementLast(hovered, clone, type)
            }
        }
        if (success) treeView.selectElements(listOf(clone))
    }

    fun insertElementLast(hovered: V, clone: V, type: Char): Boolean {
        val index = treeView.getChildren(hovered).size
        return if (treeView.canBeInserted(hovered, clone, index)) {
            treeView.addChild(hovered, clone, type, index)
        } else {
            LOGGER.warn("Cannot add child")
            false
        }
    }

    val V.parent: V? get() = treeView.getParent(this)

    val V.listOfAll: Sequence<V>
        get() {
            return sequence {
                yield(this@listOfAll)
                for (it in treeView.getChildren(this@listOfAll)) {
                    yieldAll(it.listOfAll)
                }
            }
        }

    override fun onPasteFiles(x: Float, y: Float, files: List<FileReference>) {
        val transform = getElement()
        for (it in files) {
            fileContentImporter.addChildFromFile(transform, it, FileContentImporter.SoftLinkMode.ASK, true) {}
        }
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "DragStart" -> {
                val window = window!!
                if (contains(window.mouseDownX, window.mouseDownY)) {
                    val element = getElement()
                    if (dragged?.getOriginal() != element) {
                        dragged = Draggable(
                            treeView.stringifyForCopy(element), treeView.getDragType(element), element,
                            TextPanel(treeView.getName(element), style)
                        )
                    }
                }
            }
            "Rename" -> {
                val e = getElement()
                askName(
                    windowStack,
                    x.toInt(),
                    y.toInt(),
                    NameDesc("Name"),
                    getName(e),
                    getColor = { -1 },
                    callback = { newName ->
                        setName(e, newName)
                    },
                    actionName = NameDesc("Change Name")
                )
            }
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun getCursor() = Cursor.drag

    override fun getTooltipText(x: Float, y: Float): String? {
        return if (isVisible) treeView.getTooltipText(getElement())
        else null
    }

    // multiple values can be selected
    override fun getMultiSelectablePanel() = this

    override val className get() = "TreeViewPanel"

}