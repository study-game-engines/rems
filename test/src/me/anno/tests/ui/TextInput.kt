package me.anno.tests.ui

import me.anno.animation.Type
import me.anno.animation.Type.Companion.SCALE
import me.anno.animation.Type.Companion.VEC3D
import me.anno.config.DefaultConfig.style
import me.anno.gpu.RenderDoc.disableRenderDoc
import me.anno.studio.StudioBase
import me.anno.ui.debug.TestStudio.Companion.testUI2
import me.anno.ui.input.FloatInput
import me.anno.ui.input.FloatVectorInput
import me.anno.ui.input.IntInput
import me.anno.ui.input.IntVectorInput
import me.anno.ui.input.components.PureTextInput

fun main() {
    // arrow keys were broken because of class names / action manager
    disableRenderDoc()
    val ti = PureTextInput(style).setValue("103212", false) // works
    val fi = FloatInput("Float", "", 103212f, Type.DOUBLE, style) // broken
    val ii = IntInput("Int", "", 103212, style) // broken
    val fvi = FloatVectorInput("Float Vector", "", SCALE, style)
    val ivi = IntVectorInput("Int Vector", "", VEC3D, style)
    testUI2("Text Input") {
        StudioBase.instance!!.enableVSync = true
        listOf(ti, fi, ii, fvi, ivi)
    }
}