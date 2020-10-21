package me.anno.ui.debug.console

import me.anno.gpu.GFX
import me.anno.input.MouseButton
import me.anno.studio.RemsStudio
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.ui.style.Style

class COFullscreen(style: Style): ScrollPanelY(Padding(5), AxisAlignment.CENTER, style) {
    override fun onBackSpaceKey(x: Float, y: Float) {
        RemsStudio.windowStack.pop().destroy()
    }
    override fun onSelectAll(x: Float, y: Float) {
        GFX.inFocus.clear()
        GFX.inFocus.addAll((child as PanelList).children)
    }
    override fun onDoubleClick(x: Float, y: Float, button: MouseButton) {
        onSelectAll(x,y)
    }
}