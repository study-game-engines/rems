package me.anno.io.files.inner

import me.anno.ecs.prefab.Prefab
import me.anno.ecs.prefab.PrefabReadable
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import java.io.ByteArrayInputStream
import java.io.InputStream

open class InnerPrefabFile(
    absolutePath: String, relativePath: String, _parent: FileReference,
    var prefab: Prefab
) : InnerFile(absolutePath, relativePath, false, _parent), PrefabReadable {

    init {
        val size = Int.MAX_VALUE.toLong()
        this.size = size
        this.compressedSize = size
        prefab.source = this
    }

    val text by lazy { JsonStringWriter.toText(prefab, InvalidRef) }
    val bytes by lazy { text.encodeToByteArray() }

    // it's a prefab, not a zip; never ever
    override fun isSerializedFolder(): Boolean = false
    override fun listChildren(): List<FileReference> = emptyList()

    override fun readTextSync() = text
    override fun readBytesSync() = bytes
    override fun inputStreamSync(): InputStream = ByteArrayInputStream(text.encodeToByteArray())

    override fun readPrefab(): Prefab {
        return prefab
    }
}