package me.anno.ui.editor.files

import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.TextureCache
import me.anno.input.Input
import me.anno.input.Key
import me.anno.io.files.FileReference
import me.anno.io.files.thumbs.Thumbs
import me.anno.language.translation.NameDesc
import me.anno.ui.Style
import me.anno.ui.Window
import me.anno.ui.WindowStack
import me.anno.ui.base.ImagePanel
import me.anno.ui.base.menu.Menu.openMenuByPanels
import kotlin.math.max

object FileExplorerOptions {

    @JvmField
    val renameDesc = NameDesc(
        "Rename",
        "Change the name of this file",
        "ui.file.rename"
    )

    @JvmField
    val openInExplorerDesc = NameDesc(
        "Open In Explorer",
        "Show the file in your default file explorer",
        "ui.file.openInExplorer"
    )

    @JvmField
    val openInStandardProgramDesc = NameDesc(
        "Show In Standard Program",
        "Open the file using your default viewer",
        "ui.file.openInStandardProgram"
    )

    @JvmField
    val editInStandardProgramDesc = NameDesc(
        "Edit In Standard Program",
        "Edit the file using your default editor",
        "ui.file.editInStandardProgram"
    )

    @JvmField
    val copyPathDesc = NameDesc(
        "Copy Path",
        "Copy the path of the file to clipboard",
        "ui.file.copyPath"
    )

    @JvmField
    val copyNameDesc = NameDesc(
        "Copy Name",
        "Copy the name of the file to clipboard",
        "ui.file.copyName"
    )

    @JvmField
    val deleteDesc = NameDesc(
        "Delete",
        "Delete this file",
        "ui.file.delete"
    )

    @JvmField
    val pasteDesc = NameDesc(
        "Paste",
        "Paste your clipboard",
        "ui.file.paste"
    )

    @JvmField
    val openInImageViewerDesc = NameDesc(
        "Open Image Viewer",
        "If an image is too small, and you don't want to resize everything in the file explorer (mouse wheel)",
        "ui.file.openImageViewer"
    )

    val rename = FileExplorerOption(renameDesc) { p, files ->
        FileExplorerEntry.rename(p.windowStack, p as? FileExplorer, files)
    }
    val openInExplorer = FileExplorerOption(openInExplorerDesc) { _, files ->
        for (file in files) {
            file.openInExplorer()
        }
    }
    val openInStandardProgram = FileExplorerOption(openInStandardProgramDesc) { _, files ->
        for (file in files) {
            file.openInStandardProgram()
        }
    }
    val editInStandardProgram = FileExplorerOption(editInStandardProgramDesc) { _, files ->
        for (file in files) {
            file.editInStandardProgram()
        }
    }
    val copyPath = FileExplorerOption(copyPathDesc) { _, files ->
        Input.setClipboardContent(files.joinToString {
            enquoteIfNecessary(it.absolutePath)
        })
    }
    val copyName = FileExplorerOption(copyNameDesc) { _, files ->
        Input.setClipboardContent(files.joinToString {
            enquoteIfNecessary(it.name)
        })
    }
    val pinToFavourites = FileExplorerOption(
        NameDesc(
            "Pin to Favourites",
            "Add file to quick access bar",
            "ui.file.pinToFavourites"
        )
    ) { _, files ->
        Favourites.addFavouriteFiles(files)
    }
    val invalidateThumbnails = FileExplorerOption(
        NameDesc(
            "Invalidate Thumbnails",
            "Regenerates them when needed",
            "ui.file.invalidateThumbnails"
        )
    ) { _, files ->
        for (file in files) {
            Thumbs.invalidate(file)
        }
    }
    val delete = FileExplorerOption(deleteDesc) { p, files ->
        FileExplorerEntry.askToDeleteFiles(p.windowStack, p as? FileExplorer, files)
    }

    val openImageViewer = FileExplorerOption(openInImageViewerDesc) { p, files ->
        openImageViewerImpl(p.windowStack, files, p.style)
    }

    fun enquoteIfNecessary(str: String): String {
        return if (' ' in str || '"' in str) {
            "\"${str.replace("\"", "\\\"")}\""
        } else str
    }

    fun openImageViewerImpl(windowStack: WindowStack, files: List<FileReference>, style: Style) {
        val panel = object : ImagePanel(style) {

            var index = 0
            val file get() = files[index]

            override fun getTexture(): ITexture2D? {
                val tex = TextureCache[file, true] ?: Thumbs[file, max(width, height), true] ?: return null
                tex.bind(0, Filtering.NEAREST, Clamping.CLAMP)
                return tex
            }

            fun step(di: Int) {
                index = (index + di) % files.size
                invalidateDrawing()
            }

            fun prev() = step(files.size - 1)
            fun next() = step(1)

            override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
                if (button == Key.BUTTON_LEFT && !long) next()
                else super.onMouseClicked(x, y, button, long)
            }

            override fun onKeyTyped(x: Float, y: Float, key: Key) {
                when (key) {
                    Key.KEY_ARROW_LEFT, Key.KEY_ARROW_UP, Key.KEY_PAGE_UP -> prev()
                    Key.KEY_ARROW_RIGHT, Key.KEY_ARROW_DOWN, Key.KEY_PAGE_DOWN -> next()
                    else -> super.onKeyTyped(x, y, key)
                }
            }
        }.enableControls()
        panel.fill(1f)
        // todo cancel button?
        windowStack.push(Window(panel, false, windowStack))
    }
}