package me.anno.tests.gfx

import me.anno.config.DefaultConfig
import me.anno.gpu.texture.Texture2D
import me.anno.ui.base.ImagePanel
import me.anno.ui.debug.TestStudio

fun testTexture(title: String, flipY: Boolean, draw: (p: ImagePanel) -> Texture2D) {
    TestStudio.testUI3(title) {
        object : ImagePanel(DefaultConfig.style) {
            override fun getTexture() = draw(this)
        }.apply {
            // it's a debug panel, so make it movable
            allowMovement = true
            allowZoom = true
            minZoom = 1e-3f // allow zooming out 1000x
            this.flipY = flipY
            showAlpha = true
        }
    }
}