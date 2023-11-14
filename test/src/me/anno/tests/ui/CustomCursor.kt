package me.anno.tests.ui

import me.anno.config.DefaultConfig.style
import me.anno.gpu.Cursor
import me.anno.image.ImageCPUCache
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.ui.Panel
import me.anno.ui.debug.TestStudio.Companion.testUI3

fun main() {
    val image = ImageCPUCache[getReference("res://icon.png"), false]!!
    val cursor = Cursor(image.resized(32, 32, true))
    val ui = object : Panel(style) {
        override fun getCursor() = cursor
    }
    testUI3("Custom Cursor", ui)
}