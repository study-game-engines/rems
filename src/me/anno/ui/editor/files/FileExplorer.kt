package me.anno.ui.editor.files

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.windowStack
import me.anno.input.Input
import me.anno.input.Input.setClipboardContent
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.FileRootRef
import me.anno.io.zip.ZipCache
import me.anno.language.translation.NameDesc
import me.anno.studio.StudioBase
import me.anno.studio.StudioBase.Companion.addEvent
import me.anno.ui.base.Visibility
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelListMultiline
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu.askName
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.files.FileExplorerEntry.Companion.deleteFileMaybe
import me.anno.ui.input.TextInput
import me.anno.ui.style.Style
import me.anno.utils.OS
import me.anno.utils.files.Files.findNextFile
import me.anno.utils.files.Files.listFiles2
import me.anno.utils.hpc.UpdatingTask
import me.anno.utils.maths.Maths.clamp
import me.anno.utils.maths.Maths.pow
import me.anno.utils.process.BetterProcessBuilder
import me.anno.utils.structures.Compare.ifDifferent
import org.apache.logging.log4j.LogManager
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt


// done, kind of: zoom: keep mouse at item in question
// done change side ratio based on: border + 1:1 frame + 2 lines of text
// todo dynamically change aspect ratio based on content for better coverage?
// issue: then the size is no longer constant
// solution: can we get the image size quickly? using ffmpeg maybe, or implemented ourselves


// done the text size is quite small on my x360 -> get the font size for the ui from the OS :)
// todo double click is not working in touch mode?
// done make file path clickable to quickly move to a grandparent folder :)

// todo buttons for filters, then dir name, search over it?, ...
// done drag n drop; links or copy?
// done search options
// done search results below
// todo search in text files
// todo search in meta data for audio and video

// todo list view

// done a stack or history to know where we were
// todo left list of relevant places? todo drag stuff in there

abstract class FileExplorer(
    initialLocation: FileReference?,
    style: Style
) : PanelListY(style.getChild("fileExplorer")) {

    abstract fun getRightClickOptions(): List<FileExplorerOption>

    open fun openOptions(file: FileReference) {
        openMenu(getFileOptions().map {
            MenuOption(it.nameDesc) {
                it.onClick(file)
            }
        })
    }

    open fun getFileOptions(): List<FileExplorerOption> {
        // todo additional options for the game engine, e.g. create prefab, open as scene
        // todo add option to open json in specialized json editor...
        val rename = FileExplorerOption(NameDesc("Rename", "Change the name of this file", "ui.file.rename")) {
            onGotAction(0f, 0f, 0f, 0f, "Rename", false)
        }
        val openInExplorer = FileExplorerOption(openInExplorerDesc) { it.openInExplorer() }
        val copyPath = FileExplorerOption(copyPathDesc) { setClipboardContent(it.absolutePath) }
        val delete = FileExplorerOption(
            NameDesc("Delete", "Delete this file", "ui.file.delete"),
        ) { deleteFileMaybe(it) }
        return listOf(rename, openInExplorer, copyPath, delete)
    }

    abstract fun onDoubleClick(file: FileReference)

    val searchBar = TextInput("Search Term", "", false, style)
        .setChangeListener {
            searchTerm = it
            invalidate()
        }
        .setWeight(1f)

    var historyIndex = 0
    val history = arrayListOf(initialLocation ?: OS.documents)

    val folder get() = history[historyIndex]

    var searchTerm = ""
    var isValid = 0f

    var entrySize = 64f
    val minEntrySize = 32f

    val uContent = PanelListX(style)
    val content = PanelListMultiline({ a, b ->
        // define the order for the file entries:
        // first .., then folders, then files
        // first a, then z, ...
        // not all folders may be sorted
        a as FileExplorerEntry
        b as FileExplorerEntry
        (b.isParent.compareTo(a.isParent)).ifDifferent {
            val af = getReference(a.path)
            val bf = getReference(b.path)
            bf.isDirectory.compareTo(af.isDirectory).ifDifferent {
                af.name.compareTo(bf.name, true)
            }
        }
    }, style)

    var lastFiles = emptyList<String>()
    var lastSearch = true

    val favourites = PanelListY(style)

    val title = PathPanel(folder, style)

    init {
        val esi = entrySize.toInt()
        content.childWidth = esi
        content.childHeight = esi * 4 / 3
        val topBar = PanelListX(style)
        this += topBar
        topBar += title
        topBar += searchBar
        this += uContent
        title.onChangeListener = {
            switchTo(it)
            invalidate()
        }
        uContent += ScrollPanelY(
            favourites,
            Padding(1),
            style,
            AxisAlignment.MIN
        ).setWeight(1f)
        uContent += content.setWeight(3f)
    }

    fun invalidate() {
        isValid = 0f
        lastFiles = listOf("!")
        invalidateLayout()
    }

    fun removeOldFiles() {
        content.children.forEach { (it as? FileExplorerEntry)?.stopPlayback() }
        content.clear()
    }

    // todo when searching, use a thread for that
    // todo regularly sleep 0ms inside of it:
    // todo when the search term changes, kill the thread

    val searchTask = UpdatingTask("FileExplorer-Query") {}

    fun createResults() {
        searchTask.compute {

            val search = Search(searchTerm)

            var level0: List<FileReference> = folder.listFiles2()
                .filter { !it.isHidden }
            val newFiles = level0.map { it.name }
            val newSearch = search.isNotEmpty()

            if (lastFiles != newFiles || lastSearch != newSearch) {

                lastFiles = newFiles
                lastSearch = newSearch

                // when searching something, also include sub-folders up to depth of xyz
                val searchDepth = 3
                val fileLimit = 10000
                if (search.isNotEmpty() && level0.size < fileLimit) {
                    var lastLevel = level0
                    var nextLevel = ArrayList<FileReference>()
                    for (i in 0 until searchDepth) {
                        for (file in lastLevel) {
                            if (file.isHidden) continue
                            if (file.isDirectory || when (file.lcExtension) {
                                    "zip", "rar", "7z", "s7z", "jar", "tar", "gz", "xz",
                                    "bz2", "lz", "lz4", "lzma", "lzo", "z", "zst",
                                    "unitypackage" -> file.isPacked.value
                                    else -> false
                                }
                            ) {
                                nextLevel.addAll(file.listChildren() ?: continue)
                            }
                            Thread.sleep(0)
                        }
                        level0 = level0 + nextLevel
                        if (level0.size > fileLimit) break
                        lastLevel = nextLevel
                        nextLevel = ArrayList()
                        Thread.sleep(0)
                    }
                }

                // be cancellable
                Thread.sleep(0)

                GFX.addGPUTask(1) {
                    removeOldFiles()
                }

                val parent = folder.getParent()
                if (parent != null) {
                    GFX.addGPUTask(1) {
                        // option to go up a folder
                        val fe = FileExplorerEntry(this, true, parent, style)
                        content += fe
                    }
                }

                val tmpCount = 64
                var tmpList = ArrayList<FileReference>(tmpCount)

                fun put() {
                    if (tmpList.isNotEmpty()) {
                        val list = tmpList
                        tmpList = ArrayList(tmpCount)
                        addEvent {
                            // check if the folder is still the same
                            if (lastFiles === newFiles && lastSearch == newSearch) {
                                for (file in list) {
                                    content += FileExplorerEntry(this, false, file, style)
                                }
                                // force layout update
                                Input.invalidateLayout()
                            }
                        }
                    }
                }

                for (file in level0.filter { it.isDirectory }) {
                    tmpList.add(file)
                    if (tmpList.size >= tmpCount) {
                        put()
                        Thread.sleep(0)
                    }
                }

                for (file in level0.filter { !it.isDirectory }) {
                    tmpList.add(file)
                    if (tmpList.size >= tmpCount) {
                        put()
                        Thread.sleep(0)
                    }
                }

                put()

                Thread.sleep(0)

            } else {

                val fe = content.children.filterIsInstance<FileExplorerEntry>()
                for (it in fe) {
                    it.visibility = Visibility[search.matches(getReference(it.path).name)]
                }

            }
        }
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        if (isValid <= 0f) {
            isValid = 5f // depending on amount of files?
            title.file = folder// ?.toString() ?: "This Computer"
            title.tooltip = if (folder == FileRootRef) "This Computer" else folder.toString()
            createResults()
        } else isValid -= GFX.deltaTime
    }

    override fun onPasteFiles(x: Float, y: Float, files: List<FileReference>) {
        // create links? or truly copy them?
        // or just switch?
        if (files.size > 1) {
            copyIntoCurrent(files)
        } else {
            openMenu(
                listOf(
                    MenuOption(NameDesc("Switch To")) {
                        switchTo(files.first())
                    },
                    MenuOption(NameDesc("Copy")) {
                        thread(name = "copying files") {
                            copyIntoCurrent(files)
                        }
                    },
                    MenuOption(NameDesc("Create Links")) {
                        thread(name = "creating links") {
                            createLinksIntoCurrent(files)
                        }
                    },
                    MenuOption(NameDesc("Cancel")) {}
                ))
        }
    }

    fun copyIntoCurrent(files: List<FileReference>) {
        // done progress bar
        // todo cancellable
        // and then async as well
        val progress = StudioBase.addProgressBar("Bytes", files.sumOf { it.length() }.toDouble())
        for (file in files) {
            val newFile = findNextFile(folder, file, 1, '-', 1)
            newFile.writeFile(file) { progress.add(it) }
        }
        invalidate()
    }

    fun createLinksIntoCurrent(files: List<FileReference>) {
        // done progress bar
        // todo cancellable
        val progress = StudioBase.addProgressBar("Files", files.size.toDouble())
        var tmp: File? = null
        loop@ for (file in files) {
            when {
                OS.isWindows -> {
                    val newFile = findNextFile(folder, file, "lnk", 1, '-', 1)
                    if (tmp == null) tmp = File.createTempFile("create-link", ".ps1")
                    tmp!!
                    tmp.writeText(
                        "" + // param ( [string]$SourceExe, [string]$DestinationPath )
                                "\$WshShell = New-Object -comObject WScript.Shell\n" +
                                "\$Shortcut = \$WshShell.CreateShortcut(\"${newFile.absolutePath}\")\n" +
                                "\$Shortcut.TargetPath = \"${file.absolutePath}\"\n" +
                                "\$Shortcut.Save()"
                    )
                    // PowerShell.exe -ExecutionPolicy Unrestricted -command "C:\temp\TestPS.ps1"
                    val builder = BetterProcessBuilder("PowerShell.exe", 16, false)
                    builder.add("-ExecutionPolicy")
                    builder.add("Unrestricted")
                    builder.add("-command")
                    builder.add(tmp.absolutePath)
                    builder.startAndPrint().waitFor()
                    invalidate()
                    progress.add(1.0)
                }
                OS.isLinux || OS.isMac -> {
                    val newFile = findNextFile(folder, file, 1, '-', 1)
                    // create symbolic link
                    // ln -s target_file link_name
                    val builder = BetterProcessBuilder("ln", 3, false)
                    builder.add("-s") // symbolic link
                    builder.add(file.absolutePath)
                    builder.add(newFile.absolutePath)
                    builder.startAndPrint()
                    invalidate()
                    progress.add(1.0)
                }
                OS.isAndroid -> {
                    LOGGER.warn("Unsupported OS for creating links.. how would you do that?")
                    progress.cancel()
                    return
                }
                else -> {
                    LOGGER.warn("Unknown OS, don't know how to create links")
                    progress.cancel()
                    return
                }
            }
        }
        progress.finish()
        try {
            tmp?.delete()
        } catch (e: Exception) {
        }
    }

    abstract override fun onPaste(x: Float, y: Float, data: String, type: String)

    override fun onGotAction(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        action: String,
        isContinuous: Boolean
    ): Boolean {
        when (action) {
            "OpenOptions" -> {
                val home = folder
                openMenu(
                    listOf(
                        MenuOption(NameDesc("Create Folder", "Creates a new directory", "ui.newFolder")) {
                            askName(
                                NameDesc("Name", "", "ui.newFolder.askName"),
                                "",
                                NameDesc("Create"),
                                { -1 }) {
                                val validName = it.toAllowedFilename()
                                if (validName != null) {
                                    getReference(home, validName).mkdirs()
                                    invalidate()
                                }
                            }
                        },
                        MenuOption(openInExplorerDesc) { folder.openInExplorer() },
                        MenuOption(copyPathDesc) { setClipboardContent(folder.absolutePath) }
                    ) + getRightClickOptions().map {
                        MenuOption(it.nameDesc) {
                            it.onClick(folder)
                        }
                    })
            }
            "Back", "Backward" -> back()
            "Forward" -> forward()
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    fun back() {
        if (historyIndex > 0) {
            historyIndex--
            invalidate()
        } else {
            val element = folder.getParent() ?: return
            history.clear()
            history.add(element)
            historyIndex = 0
            invalidate()
        }
    }

    fun forward() {
        if (historyIndex + 1 < history.size) {
            historyIndex++
            invalidate()
        } else {
            LOGGER.info("End of history reached!")
        }
    }

    fun switchTo(folder: FileReference?) {
        folder ?: return
        val windowsLink = folder.windowsLnk.value
        if (windowsLink != null) {
            val dst = getReference(windowsLink.absolutePath)
            if (dst.exists) {
                switchTo(dst)
            } else {
                switchTo(folder.getParent())
            }
        } else if (!canSensiblyEnter(folder)) {
            switchTo(folder.getParent())
        } else {
            while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
            history.add(folder)
            historyIndex++
            invalidate()
        }
    }

    fun canSensiblyEnter(file: FileReference): Boolean {
        return file.isDirectory || (file.isSomeKindOfDirectory &&
                ZipCache.unzip(file, false)?.listChildren()?.isEmpty() == false)
    }

    var hoveredItemIndex = 0
    var hoverFractionY = 0f

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        super.onMouseMoved(x, y, dx, dy)
        if (!Input.isControlDown) {
            // find which item is being hovered
            hoveredItemIndex = content.getItemIndexAt(x, y)
            hoverFractionY = clamp(content.getItemFractionY(y), 0.25f, 0.75f)
        }
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float, byMouse: Boolean) {
        if (Input.isControlDown) {
            entrySize = clamp(
                entrySize * pow(1.05f, dy - dx),
                minEntrySize,
                max(w - content.spacing * 2f - 1f, 20f)
            )
            val esi = entrySize.toInt()
            content.childWidth = esi
            // define the aspect ratio by 2 lines of space for the name
            val sample = content.firstOfAll { it is TextPanel } as? TextPanel
            val sampleFont = sample?.font ?: style.getFont("text", DefaultConfig.defaultFont)
            val textSize = sampleFont.sizeInt
            content.childHeight = esi + (textSize * 2.5f).roundToInt()
            // scroll to hoverItemIndex, hoverFractionY
            content.scrollTo(hoveredItemIndex, hoverFractionY)
        } else super.onMouseWheel(x, y, dx, dy, byMouse)
    }

    // multiple elements can be selected
    override fun getMultiSelectablePanel() = this

    override val className get() = "FileExplorer"

    companion object {
        private val LOGGER = LogManager.getLogger(FileExplorer::class)
        private val forbiddenConfig =
            DefaultConfig["files.forbiddenCharacters", "<>:\"/\\|?*"] + String(CharArray(32) { it.toChar() })
        val forbiddenCharacters = forbiddenConfig.toHashSet()

        fun invalidateFileExplorers() {
            windowStack.forEach { window ->
                window.panel.forAll {
                    if (it is FileExplorer) {
                        it.invalidate()
                    }
                }
            }
        }

        val openInExplorerDesc = NameDesc(
            "Open In Explorer",
            "Show the file in your default file explorer",
            "ui.file.openInExplorer"
        )

        val copyPathDesc = NameDesc(
            "Copy Path",
            "Copy the path of the file to clipboard",
            "ui.file.copyPath"
        )

    }

}