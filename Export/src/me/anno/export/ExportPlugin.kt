package me.anno.export

import me.anno.config.DefaultConfig.style
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.engine.Events.addEvent
import me.anno.engine.projects.GameEngineProject
import me.anno.export.platform.LinuxPlatforms
import me.anno.export.platform.MacOSPlatforms
import me.anno.export.platform.WindowsPlatforms
import me.anno.extensions.events.EventHandler
import me.anno.extensions.plugins.Plugin
import me.anno.gpu.GFX
import me.anno.io.Saveable.Companion.registerCustomClass
import me.anno.io.config.ConfigBasics.configFolder
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.language.translation.NameDesc
import me.anno.ui.Window
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.askName
import me.anno.ui.base.menu.Menu.msg
import me.anno.ui.editor.OptionBar
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.EnumInput
import me.anno.utils.Clock
import me.anno.utils.Color.white
import me.anno.utils.structures.lists.Lists.firstInstanceOrNull
import java.io.IOException
import kotlin.concurrent.thread

class ExportPlugin : Plugin() {

    val configFile get() = configFolder.getChild("Export.json")

    override fun onEnable() {
        super.onEnable()
        registerListener(this)
        registerCustomClass(ExportSettings())
        registerCustomClass(LinuxPlatforms())
        registerCustomClass(WindowsPlatforms())
        registerCustomClass(MacOSPlatforms())
        addEvent(::registerExportMenu)
    }

    override fun onDisable() {
        super.onDisable()
        addEvent(::removeExistingExportButton)
    }

    @EventHandler
    fun onLoadProject(event: GameEngineProject.ProjectLoadedEvent) {
        // before we load a project, there isn't really an OptionBar ->
        // every time a project is loaded, this needs to be called
        registerExportMenu()
    }

    fun removeExistingExportButton() {
        // remove existing export button from main menu
        for (window in GFX.windows) {
            for (window1 in window.windowStack) {
                val bar = window1.panel.listOfAll
                    .firstInstanceOrNull<OptionBar>() ?: continue
                bar.removeMajor("Export")
            }
        }
    }

    fun registerExportMenu() {
        removeExistingExportButton()
        // inject export button into main menu
        for (window in GFX.windows) {
            for (window1 in window.windowStack) {
                val bar = window1.panel.listOfAll
                    .firstInstanceOrNull<OptionBar>() ?: continue
                bar.addMajor("Export", ::openExportMenu)
            }
        }
    }

    fun loadPresets(): List<ExportSettings> {
        if (configFile.exists) {
            return try {
                JsonStringReader.read(configFile, workspace, true)
                    .filterIsInstance<ExportSettings>()
                    .sortedByDescending { it.lastUsed }
            } catch (e: IOException) {
                e.printStackTrace()
                return emptyList()
            }
        } else return emptyList()
    }

    fun storePresets(presets: List<ExportSettings>) {
        configFile.writeText(JsonStringWriter.toText(presets, workspace))
    }

    fun openExportMenu() {
        val presets = loadPresets().sortedBy { it.name.lowercase() }
        val ui = PanelListY(style)
        fun reloadUI() {
            Menu.close(ui)
            openExportMenu()
        }
        // export setting chooser: a list of saveable presets
        val body = PanelListY(style)
        lateinit var preset: ExportSettings
        ui.add(EnumInput(
            NameDesc("Preset"), NameDesc("Please Choose"),
            listOf(NameDesc("New Preset")) +
                    presets.map { NameDesc(it.name, it.description, "") }, style
        ).setChangeListener { _, index, _ ->
            if (index == 0) {
                // create a new preset -> ask user for name
                askName(
                    GFX.someWindow.windowStack,
                    NameDesc(), "Preset Name", NameDesc("Create"), { white }, {
                        Menu.close(ui)
                        val newList = presets + ExportSettings().apply { name = it.trim() }
                        storePresets(newList)
                        openExportMenu()
                    }
                )
            } else {

                preset = presets[index - 1]
                preset.lastUsed = System.currentTimeMillis()
                storePresets(presets)

                body.clear()
                // inputs
                preset.createInspector(body, style) {
                    val cat = SettingCategory(it, style)
                    cat.show2()
                    body.add(cat)
                    cat
                }
                // buttons
                body.add(TextButton("Export", style)
                    .addLeftClickListener {
                        val clock = Clock()
                        val progress = GFX.someWindow.addProgressBar("Export", "Files", 1.0)
                        progress.intFormatting = true
                        thread(name = "Export") {
                            ExportProcess.execute(GameEngineProject.currentProject!!, preset, progress)
                            clock.stop("Export")
                            addEvent { msg(NameDesc("Export Finished!")) }
                        }
                    })
                body.add(TextButton("Save Preset", style)
                    .addLeftClickListener {
                        storePresets(presets)
                        msg(NameDesc("Saved Preset!"))
                    })
                body.add(TextButton("Save Preset As...", style)
                    .addLeftClickListener {
                        askName(ui.windowStack, NameDesc("Preset Name"), preset.name,
                            NameDesc("Save"), { -1 }) {
                            val newPreset = preset.clone()
                            newPreset.name = it.trim()
                            storePresets(presets + newPreset)
                            msg(NameDesc("Saved Preset!"))
                            reloadUI()
                        }
                    })
                body.add(TextButton("Delete Preset", style)
                    .addLeftClickListener {
                        ask(ui.windowStack, NameDesc("Delete ${preset.name}?")) {
                            storePresets(presets.filter { it !== preset })
                            reloadUI()
                        }
                    })
            }
        })
        ui.add(body)
        // todo open quick export: most recent 3 export settings
        val ws = GFX.someWindow.windowStack
        ws.add(Window(ui, false, ws))
    }
}