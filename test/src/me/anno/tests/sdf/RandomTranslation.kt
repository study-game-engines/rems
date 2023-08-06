package me.anno.tests.sdf

import me.anno.engine.ECSRegistry
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.gpu.GFXBase
import me.anno.sdf.arrays.SDFArrayMapper
import me.anno.sdf.random.SDFRandomTranslation
import me.anno.sdf.shapes.SDFBox

fun main() {

    ECSRegistry.init()

    val array = SDFArrayMapper()
    array.cellSize.set(2f)
    array.count.set(10, 1, 10)

    val shape = SDFBox()
    shape.addChild(array)
    shape.addChild(SDFRandomTranslation())

    GFXBase.disableRenderDoc()
    testSceneWithUI("SDFRandomTranslation", shape)
}