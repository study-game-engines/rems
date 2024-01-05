package me.anno.engine.ui

import me.anno.ecs.Entity
import me.anno.ecs.EntityQuery.getComponent
import me.anno.ecs.EntityStats.totalNumComponents
import me.anno.ecs.EntityStats.totalNumEntities
import me.anno.ecs.components.light.LightComponent
import me.anno.ecs.prefab.Hierarchy
import me.anno.ecs.prefab.Prefab
import me.anno.ecs.prefab.PrefabInspector.Companion.currentInspector
import me.anno.ecs.prefab.PrefabInspector.Companion.formatWarning
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.ecs.prefab.change.CAdd
import me.anno.ecs.prefab.change.CSet
import me.anno.ecs.prefab.change.Path
import me.anno.engine.ui.render.RenderView
import me.anno.engine.ui.scenetabs.ECSSceneTabs
import me.anno.io.ISaveable
import me.anno.io.NamedSaveable
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.length
import me.anno.studio.StudioBase
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.menu.Menu.menuSeparator1
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.editor.files.FileContentImporter
import me.anno.ui.editor.files.Search
import me.anno.ui.editor.treeView.TreeView
import me.anno.utils.Color.black
import me.anno.utils.Color.mixARGB
import me.anno.utils.Color.mixARGB2
import me.anno.utils.Color.normARGB
import me.anno.utils.Color.white
import me.anno.utils.strings.StringHelper.camelCaseToTitle
import me.anno.utils.strings.StringHelper.shorten
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.flatten
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager

open class ECSTreeView(style: Style) : TreeView<ISaveable>(
    ECSFileImporter as FileContentImporter<ISaveable>,
    showSymbols = true, style
) {

    val inspector get() = currentInspector!!

    override fun listSources(): List<ISaveable> {
        val world = EditorState.prefab?.getSampleInstance()// ?: library.world
        return if (world != null) listOf(world) else emptyList()
    }

    override fun isValidElement(element: Any?): Boolean {
        return element is PrefabSaveable
    }

    override fun addChild(element: ISaveable, child: Any, type: Char, index: Int): Boolean {
        element as PrefabSaveable
        val prefab: Prefab
        val prefabPath: Path
        val root = element.root
        if (root.prefab == null) root.ensurePrefab()
        when (child) {
            is Prefab -> {
                prefab = child
                prefabPath = Path.ROOT_PATH
            }
            is PrefabSaveable -> {
                prefab = root.prefab!!
                prefabPath = child.prefabPath
            }
            else -> {
                LOGGER.warn("Unknown type $child")
                return false
            }
        }
        LOGGER.info("Input: ${element.prefab?.adds}")
        Hierarchy.add(prefab, prefabPath, element, type, index)
        LOGGER.info("Output: ${element.prefab?.adds}")
        return true
    }

    override fun fulfillsSearch(element: ISaveable, name: String, ttt: String?, search: Search): Boolean {
        return if (element is NamedSaveable) {
            val builder = StringBuilder()
            builder.append(element.name)
            if (!element.description.isBlank2()) builder.append(' ').append(element.description)
            if (element is PrefabSaveable) builder.append(' ').append(element.lastWarning ?: "")
            if (ttt != null && !ttt.isBlank2()) builder.append(' ').append(ttt)
            search.matches(builder)
        } else super.fulfillsSearch(element, name, ttt, search)
    }

    override fun removeChild(parent: ISaveable, child: ISaveable) {
        // todo somehow the window element cannot be removed
        // todo this generally is broken...
        if (parent is PrefabSaveable && child is PrefabSaveable) {
            parent.root.ensurePrefab()
            parent.ensurePrefab()
            child.ensurePrefab()
            LOGGER.info("Trying to remove element ${child.className}/${child.ref}/${child.prefabPath} from ${parent.className}/${parent.ref}/${parent.prefabPath}")
            EditorState.selection = EditorState.selection.filter { it !in child.listOfHierarchy }
            Hierarchy.removePathFromPrefab(parent.root.prefab!!, child)
        } else throw NotImplementedError()
    }

    override fun paste(hovered: ISaveable, original: ISaveable?, relativeY: Float, data: String) {
        val type1 = findType(original, hovered) ?: ' '
        if (original != null && canBeMoved(hovered, original)) {
            LOGGER.info("Movable change")
            var allowMove = true
            if (original is PrefabSaveable && hovered is PrefabSaveable) {
                LOGGER.info("Both are PrefabSavable")
                val srcPrefab = original.prefab
                val dstPrefab = hovered.prefab
                if (srcPrefab != null && dstPrefab != null) {
                    LOGGER.info("Both have Prefab")
                    if (srcPrefab.addsByItself(original.prefabPath)) {
                        // if possible, do change on prefab level
                        //  - if has CAdd, move that and CSets
                        val oldRoot = original.prefabPath
                        LOGGER.info("srcPrefab added original, oldRoot: '$oldRoot'")
                        val adds = ArrayList<CAdd>()
                        for ((_, v) in srcPrefab.adds) {
                            v.removeIf {
                                val isSelf = it.matches(oldRoot)
                                val isUnderSelf = oldRoot.getRestIfStartsWith(it.path, 0)
                                if (isSelf || isUnderSelf != null) {
                                    println("Removing $it, because $isSelf || $isUnderSelf")
                                    val path = isUnderSelf ?: Path.ROOT_PATH // correct???
                                    adds.add(it.withPath(path, false))
                                    srcPrefab.addedPaths?.remove(it.path to it.nameId)
                                    true
                                } else false
                            }
                        }
                        val sets = ArrayList<CSet>()
                        srcPrefab.sets.removeIf { k1, k2, v ->
                            val newPath = oldRoot.getRestIfStartsWith(k1, 0)
                            if (newPath != null) {
                                sets.add(CSet(newPath, k2, v))
                                true
                            } else false
                        }
                        insertElement(relativeY, hovered, dstPrefab, PrefabObject(adds, sets))
                        return
                    } else {
                        LOGGER.info("SrcPrefab doesn't add it :/, ${original.prefabPath} !in ${srcPrefab.adds}")
                        // to do copy it somehow???
                        allowMove = false
                    }
                }
            }
            if (allowMove) {
                moveChange {
                    removeFromParent(original)
                    insertElement(relativeY, hovered, original, type1)
                }
                return
            }
        }

        LOGGER.info("Must-copy change")
        // if we have prefab data, clone on prefab level:
        //  - if has CAdd for original, copy that and CSets
        if (original is PrefabSaveable && hovered is PrefabSaveable) {
            LOGGER.info("Both are PrefabSaveable")
            val srcPrefab = original.prefab
            val dstPrefab = hovered.prefab
            if (srcPrefab != null && dstPrefab != null) {
                LOGGER.info("Both have Prefabs")
                if (srcPrefab.addsByItself(original.prefabPath)) {
                    val oldRoot = original.prefabPath
                    LOGGER.info("SrcPrefab added original, oldRoot: '$oldRoot'")
                    val isRoot = oldRoot == Path.ROOT_PATH
                    val selfAdd = if (isRoot) listOf(
                        CAdd(Path.ROOT_PATH, 'e', original.className, Path.generateRandomId(), srcPrefab.prefab)
                    ) else emptyList()
                    val prefix = (if (isRoot) {
                        // todo find correct index
                        val index = -1
                        selfAdd.first().getSetterPath(index)
                    } else Path.ROOT_PATH)
                    val adds = selfAdd + srcPrefab.adds.entries
                        .sortedBy { it.key.depth }
                        .map { (_, v) ->
                            v.mapNotNull {
                                val isSelf = it.matches(oldRoot)
                                val isUnderSelf = oldRoot.getRestIfStartsWith(it.path, 0)
                                if (isSelf || isUnderSelf != null) {
                                    val path1 = prefix + (isUnderSelf ?: Path.ROOT_PATH)
                                    it.withPath(path1, false)
                                } else null
                            }
                        }.flatten()
                    val sets = srcPrefab.sets.mapNotNull { path, k, v ->
                        val newPath = oldRoot.getRestIfStartsWith(path, 0)
                        if (newPath != null) {
                            CSet(newPath, k, v)
                        } else null
                    }
                    insertElement(relativeY, hovered, dstPrefab, PrefabObject(adds, sets))
                    return
                }
            }
        }
        val clone = JsonStringReader.read(data, StudioBase.workspace, true).firstOrNull() ?: return
        moveChange {
            insertElement(relativeY, hovered, clone, type1)
        }
    }

    class PrefabObject(val adds: List<CAdd>, val sets: List<CSet>)

    fun insertElement(relativeY: Float, hovered: PrefabSaveable, dstPrefab: Prefab, clone: PrefabObject) {
        val mode = getInsertMode(relativeY, hovered)
        val (path, index) = when (mode) {
            InsertMode.BEFORE -> addRelative(hovered, 0)
            InsertMode.AFTER -> addRelative(hovered, 1)
            InsertMode.LAST -> insertElementLast(hovered)
        }
        dstPrefab.invalidateInstance()
        for (add in clone.adds) {
            val isPrimary = add.path == Path.ROOT_PATH
            val newAdd = add.withPath(path + add.path, false)
            dstPrefab.add(
                // if we change a path here, we need to change it below, too
                newAdd,
                if (isPrimary) index
                else add.path.index
            )
        }
        val add = clone.adds.first()
        val path1 = path + Path(Path.ROOT_PATH, add.nameId, index, add.type)
        for (set in clone.sets) {
            dstPrefab[path1 + set.path, set.name] = set.value
        }
        // todo adjust indices of all lists that were changed (?)
        //  - only this list was changed, and the one where we removed the item
        // finding the index can be complicated though, as we need to respect the hierarchy
        // need to invalidate structure (TreeView)
        invalidateLayout()
    }

    fun addRelative(sibling: PrefabSaveable, delta: Int): Pair<Path, Int> {
        val parent = getParent(sibling)!!
        val index = getIndexInParent(parent, sibling) + delta
        return (sibling.prefabPath.parent ?: Path.ROOT_PATH) to index
    }

    fun insertElementLast(hovered: PrefabSaveable): Pair<Path, Int> {
        val index = getChildren(hovered).size
        return hovered.prefabPath to index
    }

    override fun removeRoot(root: ISaveable) {
        LOGGER.warn("Cannot remove root")
    }

    override fun destroy(element: ISaveable) {
        if (element is PrefabSaveable) element.onDestroy()
    }

    private fun getWarning(element: PrefabSaveable): String? {
        for (warn in element.getReflections().debugWarnings) {
            val value = warn.getter.call(element)
            if (value != null) return formatWarning(warn.name, value)
        }
        if (element.isCollapsed) {// could become expensive... so only go to a certain depth
            for (childType in element.listChildTypes()) {
                for (child in element.getChildListByType(childType)) {
                    for (warn in child.getReflections().debugWarnings) {
                        val value = warn.getter.call(child)
                        if (value != null) return formatWarning(warn.name, value)
                    }
                }
            }
        }
        return null
    }

    private fun hasWarning(element: PrefabSaveable): Boolean {
        for (warn in element.getReflections().debugWarnings) {
            val value = warn.getter.call(element)
            if (value != null) return true
        }
        if (element.isCollapsed) {// could become expensive... so only go to a certain depth
            for (childType in element.listChildTypes()) {
                for (child in element.getChildListByType(childType)) {
                    for (warn in child.getReflections().debugWarnings) {
                        val value = warn.getter.call(child)
                        if (value != null) return true
                    }
                }
            }
        }
        return false
    }

    private fun getFocusColor(isEnabled: Boolean, isInFocus: Boolean, isIndirectlyInFocus: Boolean): Int {
        return if (isEnabled) {
            if (isInFocus) 0xffcc15
            else if (isIndirectlyInFocus) 0xddccaa
            else 0xcccccc
        } else {
            if (isInFocus) 0xcc15ff
            else if (isIndirectlyInFocus) 0x442255
            else 0x333333
        }
    }

    override fun getLocalColor(element: ISaveable, isHovered: Boolean, isInFocus: Boolean): Int {

        val isInFocus2 = isInFocus || (element is PrefabSaveable && element in EditorState.selection)
        // show a special color, if the current element contains something selected

        val isIndirectlyInFocus = !isInFocus2 && EditorState.selection.any2 {
            it is PrefabSaveable && it.anyInHierarchy { p -> p === element }
        }

        val isEnabled = if (element is PrefabSaveable) element.allInHierarchy { it.isEnabled } else true
        var color = getFocusColor(isEnabled, isInFocus2, isIndirectlyInFocus)

        // check if it is a direct prefab, ours, or inside a prefab
        if (element is PrefabSaveable) {
            val prefab = element.prefab
            val path = element.prefabPath
            if (prefab != null && path != Path.ROOT_PATH) {
                val add = prefab.find(path)
                val isOurs = add != null && add.prefab == InvalidRef
                if (!isOurs) {
                    val isPrefab = add != null
                    var blueIntensity = if (isPrefab) 1f else 0.6f
                    if (isInFocus2 || isIndirectlyInFocus) {
                        blueIntensity *= 0.5f
                    }
                    color = mixARGB2(color, 0x3160f7, blueIntensity * 0.6f)
                }
            }
        }

        // if is light component, we can mix in its color
        val light = if (element is Entity) element.getComponent(LightComponent::class) else element as? LightComponent
        if (light != null) color = mixARGB2(color, normARGB(light.color), 0.5f)

        if (isHovered) color = mixARGB2(color, white, 0.5f)
        if (element is PrefabSaveable && hasWarning(element)) color = mixARGB(color, 0xffff00, 0.8f)
        return color or black
    }

    override fun getTooltipText(element: ISaveable): String {
        val maxLength = 100
        val warn = if (element is PrefabSaveable) getWarning(element) else null
        if (warn != null) return warn
        val desc = if (element is PrefabSaveable) element.description.shorten(maxLength).toString() else ""
        val descLn = if (desc.isEmpty()) desc else desc + "\n"
        return when (element) {
            is Panel -> element.tooltip ?: desc
            !is Entity -> desc
            else -> descLn + "${element.children.size} E + ${element.components.size} C, " +
                    "${element.totalNumEntities} E + ${element.totalNumComponents} C total"
        }
    }

    override fun getChildren(element: ISaveable): List<ISaveable> {
        return if (element is PrefabSaveable) {
            val types = element.listChildTypes()
            when (types.length) {
                0 -> emptyList()
                1 -> element.getChildListByType(types[0])
                else -> {
                    val childCount = types.sumOf { element.getChildListByType(it).size }
                    val joined = ArrayList<PrefabSaveable>(childCount)
                    for (type in types) {
                        joined += element.getChildListByType(type)
                    }
                    joined
                }
            }
        } else emptyList()
    }

    override fun isCollapsed(element: ISaveable): Boolean {
        return if (element is PrefabSaveable) element.isCollapsed else false
    }

    override fun setCollapsed(element: ISaveable, collapsed: Boolean) {
        if (element !is PrefabSaveable) return
        element.isCollapsed = collapsed

        // idc too much about saving that property; main thing is that we can collapse and expand stuff in the editor
        val path = element.prefabPath
        val prefab = element.root.prefab
        if (prefab != null && prefab.isWritable)
            prefab[path, "isCollapsed"] = collapsed

        needsTreeUpdate = true
        invalidateLayout()
    }

    override fun getDragType(element: ISaveable): String {
        return when (element) {
            is PrefabSaveable -> "PrefabSaveable"
            is Prefab -> "Prefab"
            else -> element.className
        }
    }

    override fun stringifyForCopy(element: ISaveable): String {
        if (element !is PrefabSaveable) return element.toString()
        val tab = ECSSceneTabs.currentTab ?: return ""
        val root = tab.inspector.root
        return if (element == root) {
            tab.inspector.toString()
        } else {
            Hierarchy.stringify(element)
        }
    }

    override fun getSymbol(element: ISaveable): String {
        return if (isCollapsed(element) && getChildren(element).isNotEmpty()) "📁"
        else if (element is PrefabSaveable && element.root.prefab?.isWritable == false) "\uD83D\uDD12" // lock
        else "⚪"
    }

    override fun getParent(element: ISaveable): ISaveable? {
        return (element as? PrefabSaveable)?.parent
    }

    override fun getName(element: ISaveable): String {
        element as PrefabSaveable
        val name = element.name
        return if (name.isBlank2()) element.className.camelCaseToTitle() else name
    }

    override fun setName(element: ISaveable, name: String) {
        ECSFileImporter.setName(element, name)
    }

    override fun openAddMenu(parent: ISaveable) {
        parent as PrefabSaveable
        // temporary solution:
        val prefab = parent.prefab
        if (prefab == null || prefab.isWritable) {
            // open add menu for often created entities: camera, light, nodes, ...
            // we could use, which prefabs were most often created :)
            // todo more options:
            //  undo all deletions
            //  duplicate
            val extraOptions = listOf(
                MenuOption(NameDesc("Reset all changes")) {
                    LogManager.enableLogger("Hierarchy")
                    Hierarchy.resetPrefab(prefab!!, parent.prefabPath, true)
                }.setEnabled(prefab != null),
                MenuOption(NameDesc("Reset all changes (except transform)")) {
                    LogManager.enableLogger("Hierarchy")
                    Hierarchy.resetPrefabExceptTransform(prefab!!, parent.prefabPath, true)
                }.setEnabled(prefab != null),
                menuSeparator1
            )
            val types = parent.listChildTypes()
            openMenu(
                windowStack,
                extraOptions + types.map { type ->
                    (parent.getOptionsByType(type) ?: emptyList())
                        .map { option ->
                            val title = option.title
                            MenuOption(NameDesc(title)) {
                                val sample = (option.value0 ?: option.generator()) as PrefabSaveable
                                val prefab1 = Prefab(sample.className)
                                addChild(parent, prefab1, type, -1)
                            }
                        }
                }
                    .flatten()
                    .sortedBy { it.title }
            )
        } else LOGGER.warn("Prefab is not writable!")
    }

    override fun canBeInserted(parent: ISaveable, element: ISaveable, index: Int): Boolean {
        if (parent !is PrefabSaveable) return false
        if (parent.root.prefab?.isWritable == false) return false
        return parent.getOriginal().run { this == null || index >= children.size }
    }

    override fun canBeRemoved(element: ISaveable): Boolean {
        if (element !is PrefabSaveable) {
            LOGGER.warn("Cannot remove, because not PrefabSaveable")
            return false
        }
        if (element.root.prefab?.isWritable == false) {
            LOGGER.warn("Cannot remove, because Prefab readonly")
            return false
        }
        val parent = element.parent
        if (parent == null) {
            // must not remove root
            LOGGER.warn("Cannot remove, because it's root")
            return false
        }
        val parentPrefab = parent.getOriginal()
        if (element.prefab == null) {
            return true
        }
        if (parentPrefab == null) {
            LOGGER.warn("Cannot remove, because parent prefab is null")
        }
        return parentPrefab != null
    }

    override fun selectElements(elements: List<ISaveable>) {
        ECSSceneTabs.refocus()
        EditorState.select(elements.filterIsInstance<PrefabSaveable>())
    }

    override fun focusOnElement(element: ISaveable) {
        selectElements(listOf(element))
        // focus on the element by inverting the camera transform and such...
        val windowStack = window!!.windowStack
        if (element is Entity) for (window in windowStack) {
            window.panel.forAll {
                if (it is RenderView) {
                    // not perfect, but good enough probably
                    // todo smooth lerp over .2s for orientation?
                    val aabb = element.aabb
                    val newRadius = length(aabb.deltaX, aabb.deltaY, aabb.deltaZ)
                    if (newRadius.isFinite() && newRadius > 0.0) it.radius = newRadius
                    it.orbitCenter.set(element.transform.globalPosition)
                    it.updateEditorCameraTransform()
                }
            }
        }
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        if (!tryPaste(data)) {
            super.onPaste(x, y, data, type)
        }
    }

    /**
     * returns true on success
     * */
    private fun tryPaste(data: String): Boolean {
        return when (val element = JsonStringReader.read(data, StudioBase.workspace, true).firstOrNull()) {
            is Prefab -> {
                val prefab = EditorState.prefab
                val root = prefab?.getSampleInstance() ?: return false
                addChild(root, element, ' ', -1)
                true
            }
            is PrefabSaveable -> return false
            else -> {
                LOGGER.warn("Unknown type ${element?.className}")
                false
            }
        }
    }

    override val className: String get() = "ECSTreeView"

    companion object {
        private val LOGGER = LogManager.getLogger(ECSTreeView::class)
    }
}