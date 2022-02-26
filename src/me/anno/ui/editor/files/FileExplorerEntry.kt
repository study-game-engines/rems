package me.anno.ui.editor.files

import me.anno.Engine
import me.anno.animation.LoopingState
import me.anno.audio.openal.AudioTasks
import me.anno.audio.streams.AudioFileStreamOpenAL
import me.anno.cache.instances.LastModifiedCache
import me.anno.cache.instances.VideoCache.getVideoFrame
import me.anno.config.DefaultStyle.black
import me.anno.ecs.components.shaders.effects.FSR
import me.anno.ecs.prefab.PrefabReadable
import me.anno.fonts.FontManager
import me.anno.gpu.GFX
import me.anno.gpu.GFX.clip2Dual
import me.anno.gpu.drawing.DrawTexts.drawSimpleTextCharByChar
import me.anno.gpu.drawing.DrawTextures.drawTexture
import me.anno.gpu.drawing.GFXx2D
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.TextureLib.whiteTexture
import me.anno.image.ImageCPUCache
import me.anno.image.ImageGPUCache.getInternalTexture
import me.anno.image.ImageReadable
import me.anno.image.ImageScale.scaleMaxPreview
import me.anno.input.Input
import me.anno.input.MouseButton
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReferenceAsync
import me.anno.io.files.FileReference.Companion.getReferenceOrTimeout
import me.anno.io.files.InvalidRef
import me.anno.io.files.thumbs.Thumbs
import me.anno.io.trash.TrashManager.moveToTrash
import me.anno.io.zip.InnerLinkFile
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.mixARGB
import me.anno.maths.Maths.sq
import me.anno.studio.StudioBase
import me.anno.ui.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.base.groups.PanelGroup
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.askName
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.base.text.TextPanel
import me.anno.ui.dragging.Draggable
import me.anno.ui.style.Style
import me.anno.utils.Tabs
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.strings.StringHelper.setNumber
import me.anno.utils.types.Floats.f1
import me.anno.utils.types.Strings.formatTime
import me.anno.utils.types.Strings.getImportType
import me.anno.utils.types.Strings.isBlank2
import me.anno.video.ffmpeg.FFMPEGMetadata
import me.anno.video.ffmpeg.FFMPEGMetadata.Companion.getMeta
import me.anno.video.formats.gpu.GPUFrame
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// todo when dragging files over the edge of the border, mark them as copied, or somehow make them draggable...

// todo right click, "Switch To", add option "Switch To Folder" for files, which are no folders

// todo when the aspect ratio is extreme (e.g. > 50), stretch the image artificially to maybe 10 aspect ratio

// todo cannot enter mtl file

// todo when is audio, and hovered, we need to draw the loading animation continuously as well

// todo right click to get all meta information? (properties panel in windows)

// todo images: show extra information: width, height
class FileExplorerEntry(
    private val explorer: FileExplorer,
    val isParent: Boolean, file: FileReference, style: Style
) : PanelGroup(style.getChild("fileEntry")) {

    // todo small file type (signature) icons
    // todo use search bar for sort parameters :)
    // todo or right click menu for sorting

    val path = file.absolutePath

    // todo when entering a json file, and leaving it, the icon should not be a folder!

    // todo check: do url files work (link + icon)?

    // done icons for 3d meshes
    // done icons for project files
    // done asset files like unity, and then icons for them? (we want a unity-like engine, just with Kotlin)

    // todo play mesh animations

    private var startTime = 0L

    var time = 0.0
    var frameIndex = 0
    var maxFrameIndex = 0
    val hoverPlaybackDelay = 0.5
    var scale = 1
    var previewFPS = 1.0
    var meta: FFMPEGMetadata? = null

    private val originalBackgroundColor = backgroundColor
    private val hoverBackgroundColor = mixARGB(black, originalBackgroundColor, 0.85f)
    private val darkerBackgroundColor = mixARGB(black, originalBackgroundColor, 0.7f)

    private val size get() = explorer.entrySize.toInt()

    private val importType = file.extension.getImportType()
    private var iconPath = if (isParent || file.isDirectory) {
        if (isParent) {
            "file/folder.png"
        } else {
            when (file.name.lowercase()) {
                "music", "musik", "videos", "movies" -> "file/music.png"
                "documents", "dokumente", "downloads" -> "file/text.png"
                "images", "pictures", "bilder" -> "file/image.png"
                else -> if (file.hasChildren())
                    "file/folder.png" else "file/empty_folder.png"
            }
        }
    } else {
        // actually checking the type would need to be done async, because it's slow to ready many, many files
        when (importType) {
            "Container" -> "file/compressed.png"
            "Image", "Cubemap", "Cubemap-Equ" -> "file/image.png"
            "Text" -> "file/text.png"
            "Audio", "Video" -> "file/music.png"
            "Executable" -> "file/executable.png"
            // todo link icon for .lnk and .url, and maybe .desktop
            else -> "file/document.png"
        }
    }

    private val titlePanel = TextPanel(
        when {
            isParent -> ".."
            file.nameWithoutExtension.isBlank2() && file.name.isBlank2() -> file.toString()
            file.nameWithoutExtension.isBlank2() -> file.name
            else -> file.nameWithoutExtension
        }, style
    )

    override val children: List<Panel> = listOf(titlePanel)
    override fun remove(child: Panel) {}

    init {
        titlePanel.breaksIntoMultiline = true
        titlePanel.parent = this
        titlePanel.instantTextLoading = true
    }

    private var audio: AudioFileStreamOpenAL? = null

    fun stopPlayback() {
        val audio = audio
        if (audio != null && audio.isPlaying) {
            AudioTasks.addTask(1) { audio.stop() }
            this.audio = null
        }
    }

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        val size = size
        minW = size
        minH = size + (titlePanel.font.sizeInt * 5 / 2)
        this.w = minW
        this.h = minH
    }

    // is null
    // override fun getLayoutState(): Any? = titlePanel.getLayoutState()
    private var lastTex: Any? = null
    private var lastMeta: Any? = null

    override fun tickUpdate() {
        super.tickUpdate()

        val meta = meta
        val tex = if (canBeSeen) when (val tex = getTexKey()) {
            is GPUFrame -> if (tex.isCreated) tex else null
            is Texture2D -> tex.state
            else -> tex
        } else null
        if (lastMeta !== meta || lastTex !== tex) {
            lastTex = tex
            lastMeta = meta
            invalidateDrawing()
        }

        titlePanel.canBeSeen = canBeSeen

        // todo instead invalidate all file explorers, if they contain that file
        /*val newFile = FileReference.getReference(file)
        if (newFile !== file) {
            file = newFile
            invalidateDrawing()
        }

        if (!file.exists) {
            explorer.invalidate()
        }*/

        // needs to be disabled in the future, I think
        if (ref?.isHidden == true) {
            visibility = Visibility.GONE
        }

        backgroundColor = when {
            isInFocus -> darkerBackgroundColor
            isHovered -> hoverBackgroundColor
            else -> originalBackgroundColor
        }
        updatePlaybackTime()

    }

    private fun updatePlaybackTime() {
        when (importType) {
            "Video", "Audio" -> {
                val meta = getMeta(path, true)
                this.meta = meta
                if (meta != null) {
                    val w = w
                    val h = h
                    previewFPS = min(meta.videoFPS, 120.0)
                    maxFrameIndex = max(1, (previewFPS * meta.videoDuration).toInt())
                    time = 0.0
                    frameIndex = if (isHovered) {
                        invalidateDrawing()
                        if (startTime == 0L) {
                            startTime = Engine.gameTime
                            val file = getReferenceOrTimeout(path)
                            stopPlayback()
                            if (meta.hasAudio) {
                                this.audio = AudioFileStreamOpenAL(
                                    file, LoopingState.PLAY_LOOP,
                                    -hoverPlaybackDelay, meta, 1.0
                                )
                                AudioTasks.addTask(5) {
                                    audio?.start()
                                }
                            }
                            0
                        } else {
                            time = (Engine.gameTime - startTime) * 1e-9 - hoverPlaybackDelay
                            max(0, (time * previewFPS).toInt())
                        }
                    } else {
                        startTime = 0
                        stopPlayback()
                        0
                    } % maxFrameIndex
                    scale = max(min(meta.videoWidth / w, meta.videoHeight / h), 1)
                }
            }
        }
    }

    private fun drawDefaultIcon(x0: Int, y0: Int, x1: Int, y1: Int) {
        val image = getInternalTexture(iconPath, true) ?: whiteTexture
        drawTexture(x0, y0, x1, y1, image)
    }

    private fun drawTexture(x0: Int, y0: Int, x1: Int, y1: Int, image: ITexture2D) {
        val w = x1 - x0
        val h = y1 - y0
        // if aspect ratio is extreme, use a different scale
        val (iw, ih) = scaleMaxPreview(image.w, image.h, w, h, 5)
        // todo if texture is HDR, then use reinhard tonemapping for preview, with factor of 5
        // we can use FSR to upsample the images xD
        val x = x0 + (w - iw) / 2
        val y = y0 + (h - ih) / 2
        if (image is Texture2D) image.filtering = GPUFiltering.LINEAR
        if (iw > image.w && ih > image.h) {// maybe use fsr only, when scaling < 4x
            FSR.upscale(image, x, y, iw, ih, false, backgroundColor)// ^^
        } else {
            drawTexture(x, y, iw, ih, image, -1, null)
        }
    }

    private fun getDefaultIcon() = getInternalTexture(iconPath, true)

    private fun getImage(): Any? {
        val ref = ref ?: return null
        val thumb = Thumbs.getThumbnail(ref, w, true)
        return thumb ?: getDefaultIcon()
    }

    private fun getTexKey(): Any? {
        return when (importType) {
            "Video", "Audio" -> {
                val meta = meta
                if (meta != null) {
                    if (meta.videoWidth > 0) {
                        if (time == 0.0) { // not playing
                            getImage()
                        } else time
                    } else getDefaultIcon()
                } else getDefaultIcon()
            }
            else -> getImage()
        }
    }

    val ref get() = getReferenceAsync(path)

    private fun drawImageOrThumb(x0: Int, y0: Int, x1: Int, y1: Int) {
        val w = x1 - x0
        val h = y1 - y0
        val image = Thumbs.getThumbnail(ref ?: InvalidRef, w, true) ?: getDefaultIcon() ?: whiteTexture
        val rot = (image as? Texture2D)?.rotation
        image.bind(0, GPUFiltering.LINEAR, Clamping.CLAMP)
        if (rot == null) {
            drawTexture(x0, y0, x1, y1, image)
        } else {
            val m = Matrix4fArrayList()
            rot.apply(m)
            drawTexture(m, w, h, image, -1, null)
        }
    }

    private fun drawCircle(x0: Int, y0: Int, x1: Int, y1: Int) {
        if (time < 0.0) {
            // countdown-circle, pseudo-loading
            // saves us some computations
            val relativeTime = ((hoverPlaybackDelay + time) / hoverPlaybackDelay).toFloat()
            drawLoadingCircle(relativeTime, x0, x1, y0, y1)
        }
    }

    fun getFrame(offset: Int) = getVideoFrame(
        ref ?: InvalidRef, scale, frameIndex + offset,
        videoBufferLength, previewFPS, 1000, true
    )

    private fun drawVideo(x0: Int, y0: Int, x1: Int, y1: Int) {

        // todo something with the states is broken...
        // todo only white is visible, even if there should be colors...


        val image = getFrame(0)
        if (frameIndex > 0) getFrame(videoBufferLength)
        if (image != null && image.isCreated) {
            drawTexture(
                GFX.viewportWidth, GFX.viewportHeight,
                image, -1, null
            )
            drawCircle(x0, y0, x1, y1)
        } else drawDefaultIcon(x0, y0, x1, y1)

        // show video progress on playback, e.g. hh:mm:ss/hh:mm:ss
        if (h >= 3 * titlePanel.font.sizeInt) {
            val meta = getMeta(path, true)
            if (meta != null) {

                val totalSeconds = (meta.videoDuration).roundToInt()
                val needsHours = totalSeconds >= 3600
                val seconds = max((frameIndex / previewFPS).toInt(), 0) % max(totalSeconds, 1)

                val format = if (needsHours) charHHMMSS else charMMSS
                if (needsHours) {
                    setNumber(15, totalSeconds % 60, format)
                    setNumber(12, (totalSeconds / 60) % 60, format)
                    setNumber(9, totalSeconds / 3600, format)
                    setNumber(6, seconds % 60, format)
                    setNumber(3, (seconds / 60) % 60, format)
                    setNumber(0, seconds / 3600, format)
                } else {
                    setNumber(9, totalSeconds % 60, format)
                    setNumber(6, (totalSeconds / 60) % 60, format)
                    setNumber(3, seconds % 60, format)
                    setNumber(0, seconds / 60, format)
                }

                // more clip space, and draw it a little more left and at the top
                val extra = padding / 2
                clip2Dual(
                    x0 - extra, y0 - extra, x1, y1,
                    this.lx0, this.ly0, this.lx1, this.ly1
                ) { _, _, _, _ ->
                    drawSimpleTextCharByChar(x + padding - extra, y + padding - extra, 1, format)
                }
            }
        }
    }

    private fun drawThumb(x0: Int, y0: Int, x1: Int, y1: Int) {
        /*if (file.isDirectory) {
            return drawDefaultIcon(x0, y0, x1, y1)
        }*/
        when (importType) {
            // todo audio preview???
            // todo animation preview: draw the animated skeleton
            "Video", "Audio" -> {
                val meta = meta
                if (meta != null) {
                    if (meta.videoWidth > 0) {
                        if (time == 0.0) { // not playing
                            drawImageOrThumb(x0, y0, x1, y1)
                        } else {
                            drawVideo(x0, y0, x1, y1)
                        }
                    } else {
                        drawDefaultIcon(x0, y0, x1, y1)
                        drawCircle(x0, y0, x1, y1)
                    }
                } else drawDefaultIcon(x0, y0, x1, y1)
            }
            else -> drawImageOrThumb(x0, y0, x1, y1)
        }
    }

    private fun updateTooltip() {

        // todo add created & modified information

        // if is selected, and there are multiple files selected, show group stats
        if (isInFocus && siblings.count { (it.isInFocus && it is FileExplorerEntry) || it === this } > 1) {
            val files = siblings
                .filter { it.isInFocus || it === this }
                .mapNotNull { (it as? FileExplorerEntry)?.path }
                .mapNotNull { getReferenceAsync(it) }
            tooltip = "${files.count { it.isDirectory }} folders + ${files.count { !it.isDirectory }} files\n" +
                    files.sumOf { it.length() }.formatFileSize()

        } else {

            fun getTooltip(file: FileReference): String {
                return when {
                    file.isDirectory -> {
                        // todo add number of children?, or summed size
                        file.name
                    }
                    file is InnerLinkFile -> "Link to " + getTooltip(file.link)
                    file is PrefabReadable -> {
                        val prefab = file.readPrefab()
                        file.name + "\n" +
                                "${prefab.clazzName}, ${prefab.countTotalChanges(true)} Changes"
                    }
                    file is ImageReadable -> {
                        val image = file.readImage()
                        file.name + "\n" +
                                "${image.width} x ${image.height}"
                    }
                    else -> {
                        val meta = getMeta(path, true)
                        var ttt = "${file.name}\n${file.length().formatFileSize()}"
                        if (meta != null) {
                            if (meta.hasVideo) {
                                ttt += "\n${meta.videoWidth} x ${meta.videoHeight}"
                                if (meta.videoFrameCount > 1) ttt += " @" + meta.videoFPS.f1() + " fps"
                            } else {
                                val image = ImageCPUCache.getImageWithoutGenerator(file)
                                if (image != null) {
                                    ttt += "\n${image.width} x ${image.height}"
                                }
                            }
                            if (meta.hasAudio) {
                                ttt += "\n${meta.audioSampleRate / 1000} kHz"
                            }
                            if (meta.duration > 0 && (meta.hasAudio || (meta.hasVideo && meta.videoFrameCount > 1))) {
                                ttt += "\n${meta.duration.formatTime()}"
                            }
                        }
                        ttt
                    }
                }
            }

            val ref = ref
            tooltip = if (ref != null) getTooltip(ref) else "Loading..."

        }
    }

    private var lines = 0
    private var padding = 0
    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {

        if (isHovered || isInFocus) {
            updateTooltip()
        }

        drawBackground(x0, y0, x1, y1)

        val font0 = titlePanel.font
        val font1 = FontManager.getFont(font0)
        val fontSize = font1.actualFontSize

        val x = x
        val y = y
        val w = w
        val h = h

        val extraHeight = h - w
        lines = max(ceil(extraHeight / fontSize).toInt(), 1)

        padding = w / 20

        // why not twice the padding?????
        // only once centers it...
        val remainingW = w - padding// * 2
        val remainingH = h - padding// * 2

        val textH = (lines * fontSize).toInt()
        val imageH = remainingH - textH

        clip2Dual(
            x0, y0, x1, y1,
            x + padding,
            y + padding,
            x + remainingW,
            y + padding + imageH,
            ::drawThumb
        )

        clip2Dual(
            x0, y0, x1, y1,
            x + padding,
            y + h - padding - textH,
            x + remainingW,
            y + h/* - padding*/, // only apply the padding, when not playing video?
            ::drawText
        )
    }

    /**
     * draws the title
     * */
    private fun drawText(x0: Int, y0: Int, x1: Int, y1: Int) {
        titlePanel.w = x1 - x0
        titlePanel.minW = x1 - x0
        titlePanel.calculateSize(x1 - x0, y1 - y0)
        titlePanel.backgroundColor = backgroundColor and 0xffffff
        val deltaX = ((x1 - x0) - titlePanel.minW) / 2 // centering the text
        titlePanel.x = x0 + max(0, deltaX)
        titlePanel.y = max(y0, (y0 + y1 - titlePanel.minH) / 2)
        titlePanel.w = x1 - x0
        titlePanel.h = titlePanel.minH
        titlePanel.drawText()
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "DragStart" -> {
                // todo select the file, if the mouse goes up, not down
                // why was that condition there?
                // inFocus.any { it.contains(mouseDownX, mouseDownY) } && StudioBase.dragged?.getOriginal() != file
                val selectedFiles = siblings
                    .filterIsInstance<FileExplorerEntry>()
                    .filter { it.isInFocus || it === this }
                    .map { getReferenceOrTimeout(it.path) }
                val title = selectedFiles.joinToString("\n") { it.nameWithoutExtension }
                val stringContent = selectedFiles.joinToString("\n") { it.toString() }
                val original: Any = if (selectedFiles.size == 1) selectedFiles[0] else selectedFiles
                StudioBase.dragged = Draggable(stringContent, "File", original, title, style)
            }
            "Enter" -> {
                val file = getReferenceOrTimeout(path)
                if (explorer.canSensiblyEnter(file)) {
                    explorer.switchTo(file)
                } else return false
            }
            "Rename" -> {
                val file = getReferenceOrTimeout(path)
                val title = NameDesc("Rename To...", "", "ui.file.rename2")
                askName(windowStack, x.toInt(), y.toInt(), title, file.name, NameDesc("Rename"), { -1 }, ::renameTo)
            }
            "OpenInExplorer" -> getReferenceOrTimeout(path).openInExplorer()
            "OpenInStandardProgram" -> getReferenceOrTimeout(path).openInStandardProgram()
            "EditInStandardProgram" -> getReferenceOrTimeout(path).editInStandardProgram()
            "Delete" -> deleteFileMaybe(this, getReferenceOrTimeout(path))
            "OpenOptions" -> explorer.openOptions(getReferenceOrTimeout(path))
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    fun renameTo(newName: String) {
        val allowed = newName.toAllowedFilename()
        if (allowed != null) {
            val file = getReferenceOrTimeout(path)
            val dst = file.getParent()!!.getChild(allowed)
            if (dst.exists && !allowed.equals(file.name, true)) {
                ask(windowStack, NameDesc("Override existing file?", "", "ui.file.override")) {
                    file.renameTo(dst)
                    explorer.invalidate()
                }
            } else {
                file.renameTo(dst)
                explorer.invalidate()
            }
        }
    }

    override fun onDoubleClick(x: Float, y: Float, button: MouseButton) {
        if (button.isLeft) {
            val file = getReferenceOrTimeout(path)
            if (explorer.canSensiblyEnter(file)) {
                LOGGER.info("Can enter ${file.name}? Yes!")
                explorer.switchTo(file)
            } else {
                LOGGER.info("Can enter ${file.name}? No :/")
                explorer.onDoubleClick(file)
            }
        } else super.onDoubleClick(x, y, button)
    }

    override fun onDeleteKey(x: Float, y: Float) {
        val file = getReferenceOrTimeout(path)
        // todo in Rem's Engine, we first should check, whether there are prefabs, which depend on this file
        val files = parent!!.children.mapNotNull {
            if (it is FileExplorerEntry && it.isInFocus)
                getReferenceOrTimeout(it.path) else null
        }
        if (files.size <= 1) {
            // ask, then delete (or cancel)
            deleteFileMaybe(this, file)
        } else if (files.first() === file) {
            // ask, then delete all (or cancel)
            val matches = siblings.count { (it is FileExplorerEntry && it.isInFocus) || it === this }
            val title = NameDesc(
                "Delete these files? (${matches}x, ${
                    files.sumOf { it.length() }.formatFileSize()
                })", "", "ui.file.delete.ask.many"
            )
            val moveToTrash = MenuOption(
                NameDesc(
                    "Yes",
                    "Move the file to the trash",
                    "ui.file.delete.yes"
                )
            ) {
                moveToTrash(files.map { it.toFile() }.toTypedArray())
                explorer.invalidate()
            }
            val deletePermanently = MenuOption(
                NameDesc(
                    "Yes, permanently",
                    "Deletes all selected files; forever; files cannot be recovered",
                    "ui.file.delete.many.permanently"
                )
            ) {
                files.forEach { it.deleteRecursively() }
                explorer.invalidate()
            }
            openMenu(windowStack, title, listOf(moveToTrash, dontDelete, deletePermanently))
        }
    }

    override fun onCopyRequested(x: Float, y: Float): String? {
        val files = if (isInFocus) {// multiple files maybe
            siblings.filterIsInstance<FileExplorerEntry>().map {
                getReferenceOrTimeout(it.path)
            }
        } else listOf(getReferenceOrTimeout(path))
        Input.copyFiles(files)
        return null
    }

    override fun getMultiSelectablePanel() = this

    override fun printLayout(tabDepth: Int) {
        super.printLayout(tabDepth)
        println("${Tabs.spaces(tabDepth * 2 + 2)} ${getReferenceOrTimeout(path).name}")
    }

    override val className get() = "FileEntry"

    companion object {

        private val LOGGER = LogManager.getLogger(FileExplorerEntry::class)

        val videoBufferLength = 64

        private val charHHMMSS = "hh:mm:ss/hh:mm:ss".toCharArray()
        private val charMMSS = "mm:ss/mm:ss".toCharArray()

        val dontDelete
            get() = MenuOption(
                NameDesc(
                    "No",
                    "Deletes none of the selected file; keeps them all",
                    "ui.file.delete.many.no"
                )
            ) {}

        fun deleteFileMaybe(panel: Panel, file: FileReference) {
            val title = NameDesc(
                "Delete this file? (${file.length().formatFileSize()})",
                "",
                "ui.file.delete.ask"
            )
            val moveToTrash = MenuOption(
                NameDesc(
                    "Yes",
                    "Move the file to the trash",
                    "ui.file.delete.yes"
                )
            ) {
                val file2 = file.toFile()
                moveToTrash(file2)
                FileExplorer.invalidateFileExplorers(panel)
                LastModifiedCache.invalidate(file2)
            }
            val deletePermanently = MenuOption(
                NameDesc(
                    "Yes, permanently",
                    "Deletes the file; file cannot be recovered",
                    "ui.file.delete.permanent"
                )
            ) {
                file.deleteRecursively()
                FileExplorer.invalidateFileExplorers(panel)
            }
            openMenu(
                panel.windowStack,
                title, listOf(
                    moveToTrash,
                    dontDelete,
                    deletePermanently
                )
            )
        }

        fun drawLoadingCircle(relativeTime: Float, x0: Int, x1: Int, y0: Int, y1: Int) {
            val r = 1f - sq(relativeTime * 2 - 1)
            val radius = min(y1 - y0, x1 - x0) / 2f
            GFXx2D.drawCircle(
                (x0 + x1) / 2, (y0 + y1) / 2, radius, radius, 0f,
                relativeTime * 360f * 4 / 3,
                relativeTime * 360f * 2,
                Vector4f(1f, 1f, 1f, r * 0.2f)
            )
        }

        fun drawLoadingCircle(stack: Matrix4fArrayList, relativeTime: Float) {
            GFXx3D.draw3DCircle(
                stack, 0f,
                relativeTime * 360f * 4 / 3,
                relativeTime * 360f * 2,
                Vector4f(1f, 1f, 1f, 0.2f)
            )
        }

    }


}