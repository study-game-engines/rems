package me.anno.tests.files

import me.anno.config.DefaultConfig.style
import me.anno.io.files.FileReference
import me.anno.io.files.FileRootRef
import me.anno.io.files.InvalidRef
import me.anno.io.files.Signature
import me.anno.ui.Panel
import me.anno.ui.debug.TestStudio.Companion.testUI3
import me.anno.ui.editor.files.FileContentImporter
import me.anno.ui.editor.treeView.TreeView
import me.anno.utils.types.Strings.isBlank2
import org.joml.Vector2i
import org.joml.Vector3i
import java.io.FileNotFoundException
import kotlin.concurrent.thread

val uniqueLOC = HashMap<String, HashSet<String>>(32)
val cache = HashMap<FileReference, String>(1024)
val cache2 = HashMap<Pair<FileReference, Int>, Vector3i?>(4096)
val cache3 = HashMap<FileReference, Vector2i>(4096)

fun <K, V> HashMap<K, V>.getOrPutAsync(k: K, default: V, putter: () -> V): V {
    synchronized(this) {
        return getOrPut(k) {
            thread(name = k.toString()) {
                val v = putter()
                synchronized(this) {
                    put(k, v)
                }
            }
            default
        }
    }
}

fun main() {

    // not good enough yet, don't use!

    // count unique lines of code
    // count them by language (lcExtension)
    // todo have a white and a blacklist
    // todo have a tree view of everything, where you define them
    // todo count inside zips inside zips :D
    testUI3 {
        val notCollapsed = HashSet<FileReference>(1024)
        val root = FileRootRef
        notCollapsed.add(root)
        object : TreeView<FileReference>(
            listOf(root), object : FileContentImporter<FileReference>() {
                override fun createNode(parent: FileReference?) = InvalidRef
                override fun setName(element: FileReference, name: String) {}
                override fun import(
                    parent: FileReference?,
                    file: FileReference,
                    useSoftLink: SoftLinkMode,
                    doSelect: Boolean,
                    depth: Int,
                    callback: (FileReference) -> Unit
                ) {
                }
            },
            true, style
        ) {
            override fun selectElements(elements: List<FileReference>) {}
            override fun focusOnElement(element: FileReference) {}
            override fun openAddMenu(parent: FileReference) {}
            override fun getChildren(element: FileReference) = element.listChildren() ?: emptyList()
            override fun isCollapsed(element: FileReference) = element !in notCollapsed
            override fun setCollapsed(element: FileReference, collapsed: Boolean) {
                println("collapsed[$element] = $collapsed")
                if (collapsed) notCollapsed.remove(element)
                else notCollapsed.add(element)
            }

            override fun addChild(element: FileReference, child: Any, index: Int) {}
            override fun remove(child: Panel) {}
            override fun removeChild(parent: FileReference, child: FileReference) {}
            override fun getSymbol(element: FileReference): String = "X"
            override fun getTooltipText(element: FileReference): String {
                return cache.getOrPutAsync(element, "Loading") {
                    val data = indexed(element, 3)
                    if (data == null) "?"
                    else "${data.x}, ${data.y}, ${if (data.z < 0) -1 - data.z else data.z}${if (data.z < 0) "+" else ""}"
                }
            }

            override fun getParent(element: FileReference) = element.getParent()
            override fun destroy(element: FileReference) {}
            override fun getName(element: FileReference) = element.name
            override fun setName(element: FileReference, name: String) {}
            override fun getIndexInParent(parent: FileReference, child: FileReference): Int {
                return getChildren(parent).indexOf(child)
            }

            override fun stringifyForCopy(element: FileReference): String = element.absolutePath
            override fun canBeInserted(parent: FileReference, element: FileReference, index: Int) = false
            override fun canBeRemoved(element: FileReference) = false
            override fun getDragType(element: FileReference) = "File"
            override fun isValidElement(element: Any?) = element is FileReference
            override fun toggleCollapsed(element: FileReference) {
                if (!notCollapsed.add(element)) notCollapsed.remove(element)
            }
        }
    }
}

fun indexed(file: FileReference, depth: Int): Vector3i? {
    return synchronized(cache2) {
        cache2.getOrPut(file to depth) {
            indexMaybe(file, depth)
        }
    }
}

fun indexDir(file: FileReference, depth: Int): Vector3i? {
    var childCount = 0
    var hasMore = false
    var sum = 0
    var unique = 0
    for (child in file.listChildren() ?: return null) {
        val data = try {
            indexMaybe(child, depth - 1)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (data != null) {
            if (data.z < 0) {
                hasMore = true
                childCount += -1 - data.z
            } else {
                childCount += data.z
                sum += data.x
                unique += data.y
            }
        }
    }
    return Vector3i(sum, unique, if (hasMore) -1 - childCount else childCount)
}

fun indexMaybe(file: FileReference, depth: Int): Vector3i? {
    return when {
        file.isDirectory -> {
            if (depth > 0) indexDir(file, depth)
            else Vector3i(0, 0, -1)
        }
        else -> {
            if (file.isDirectory) return null
            try {
                when (Signature.findNameSync(file)) {
                    "zip", "rar", "tar", "gzip" -> {
                        if (depth > 0) indexDir(file, depth)
                        else Vector3i(0, 0, -1)
                    }
                    else -> {
                        if (file.length() > 100_000) return null
                        try {
                            val signature = Signature.findNameSync(file)
                            if (signature == null) {
                                val data = index(file)
                                Vector3i(data.x, data.y, 1)
                            } else null
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }
            } catch (ignored: FileNotFoundException) {
                null
            }
        }
    }
}

fun index(file: FileReference): Vector2i {
    return cache3.getOrPut(file) {
        var sum = 0
        var unique = 0
        val uniqueLOC = uniqueLOC.getOrPut(file.lcExtension) { HashSet(4096) }
        val maxLineLength = 200
        val iter = file.readLinesSync(maxLineLength + 1)
        for (line in iter) {
            if (line.isBlank2() || line.length > maxLineLength) continue
            sum++
            if (uniqueLOC.add(line.trim()))
                unique++
        }
        return Vector2i(sum, unique)
    }
}