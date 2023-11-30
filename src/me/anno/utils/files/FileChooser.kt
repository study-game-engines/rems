package me.anno.utils.files

import me.anno.config.DefaultConfig.style
import me.anno.gpu.GFX
import me.anno.gpu.GFXBase
import me.anno.gpu.OSWindow
import me.anno.io.files.FileReference
import me.anno.language.translation.NameDesc
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.Window
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.files.FileExplorer
import me.anno.ui.editor.files.FileExplorerEntry
import me.anno.ui.input.EnumInput
import me.anno.ui.input.TextInput
import me.anno.utils.files.LocalFile.toGlobalFile

object FileChooser {

    @JvmStatic
    fun selectFiles(
        title: NameDesc, allowFiles: Boolean, allowFolders: Boolean,
        allowMultiples: Boolean, toSave: Boolean, startFolder: FileReference,
        filters: List<FileExtensionFilter>, callback: (List<FileReference>) -> Unit
    ) {
        if (!allowFiles && !allowFolders) {
            callback(emptyList())
            return
        }
        createFileChooser(
            title, allowFiles, allowFolders, allowMultiples, toSave,
            startFolder, filters, style, callback
        )
    }

    fun createFileChooserUI(
        allowFiles: Boolean, allowDirectories: Boolean,
        allowMultiples: Boolean, toSave: Boolean,
        startDirectory: FileReference,
        filters: List<FileExtensionFilter>,
        style: Style, callback: (List<FileReference>) -> Unit
    ): Panel {

        fun filterFiles(selected: List<FileReference>): List<FileReference> {
            var selectedFiles = selected
            if (!allowFiles || !allowDirectories) {
                selectedFiles = selectedFiles.filter {
                    if (it.isDirectory) allowDirectories else allowFiles
                }
            }
            if (selectedFiles.size > 1 && !allowMultiples) {
                selectedFiles = listOf(selectedFiles.first())
            }
            return selectedFiles
        }

        val cancel = TextButton("Cancel", style)
        val submit = TextButton("Select", style)
        submit.isInputAllowed = false

        var selected: List<FileReference> = emptyList()
        val filesList = if (toSave) {
            // if toSave, add a bar to enter a new name
            val filesList = TextInput(style)
            filesList.addChangeListener { string ->
                // change list of selected ones...
                selected = string.split("; ")
                    .map { file -> file.trim() }
                    .filter { file -> file.isNotEmpty() }
                    .map { file -> file.toGlobalFile() }
                submit.isInputAllowed = selected.isNotEmpty()
            }
            filesList.alignmentX = AxisAlignment.CENTER // todo this isn't working... why?
            filesList.base.enableSpellcheck = false
            filesList
        } else {
            TextPanel(style)
        }

        var extensions: List<String> = emptyList()
        val files = object : FileExplorer(startDirectory, style) {
            override fun filterShownFiles(file: FileReference): Boolean {
                return extensions.isEmpty() || file.lcExtension in extensions || file.isDirectory
            }

            override fun onUpdate() {
                super.onUpdate()
                val selectedList = filterFiles(content2d.children
                    .filterIsInstance<FileExplorerEntry>()
                    .filter { it.isInFocus }.map { it.ref1s })
                if (selectedList.isNotEmpty() && selectedList != selected) {
                    selected = selectedList
                    val text = if (selectedList.size == 1) {
                        selectedList.first().toLocalPath()
                    } else {
                        "${selectedList.first().getParent()?.toLocalPath()}: " +
                                selectedList.joinToString("; ") { it.name }
                    }
                    when (filesList) {
                        is TextInput -> filesList.setValue(text, false)
                        is TextPanel -> filesList.text = text
                    }
                    submit.isInputAllowed = true
                }
            }
        }
        files.weight = 1f

        val buttons = PanelListX(style)
        cancel.weight = 1f
        submit.weight = 1f
        cancel.addLeftClickListener(Menu::close)
        submit.addLeftClickListener {
            callback(selected)
            Menu.close(it)
        }
        buttons.add(cancel)
        buttons.add(submit)
        val ui = PanelListY(style)
        if (filters.isNotEmpty()) {

            fun applyFilter(filter: FileExtensionFilter) {
                extensions = filter.extensions
                files.invalidate()
            }

            val select = EnumInput(NameDesc("Filter"), filters.first().nameDesc, filters.map { it.nameDesc }, style)
            select.setChangeListener { _, index, _ ->
                applyFilter(filters[index])
            }
            applyFilter(filters.first())
            ui.add(select)
        }
        ui.add(files)
        ui.add(filesList)
        ui.add(buttons)
        return ui
    }

    var openInSeparateWindow = true
    private fun createFileChooser(
        title: NameDesc,
        allowFiles: Boolean, allowDirectories: Boolean,
        allowMultiples: Boolean, toSave: Boolean,
        startDirectory: FileReference,
        filters: List<FileExtensionFilter>,
        style: Style, callback: (List<FileReference>) -> Unit
    ): Window {
        val ui = createFileChooserUI(
            allowFiles, allowDirectories, allowMultiples,
            toSave, startDirectory, filters, style,
            callback
        )
        val baseWindow = GFX.someWindow
        return if (openInSeparateWindow || baseWindow == null) {
            // make this window a little smaller than default, so it's more obvious
            val w = OSWindow.defaultWidth * 6 / 7
            val h = OSWindow.defaultHeight * 5 / 7
            val window = GFXBase.createWindow(title.name, ui, w, h)
            window.windowStack.first()
        } else {
            val window1 = Window(ui, false, baseWindow.windowStack)
            baseWindow.windowStack.add(window1)
            window1
        }
    }
}