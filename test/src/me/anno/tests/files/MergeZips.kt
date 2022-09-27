package me.anno.tests.files

import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.utils.OS.documents
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main() {

    val files = HashMap<String, ByteArray>()

    fun add(file: FileReference, path: String = "") {
        if (file.isDirectory || path.isEmpty()) {
            for (child in file.listChildren() ?: emptyList()) {
                add(child, if (path.isEmpty()) child.name else "$path/${child.name}")
            }
        } else {
            files[path] = file.readBytesSync()
        }
    }

    val src = getReference(documents, "IdeaProjects/RobotSteve")
    add(src.getChild("lib/luaj-jse-3.0.2.jar"))
    add(src.getChild("target/RobotSteve-1.0-SNAPSHOT.jar"))

    val dst = documents.getChild("Minecraft-Server/plugins/RobotSteve-1.0-SNAPSHOT.jar")
    val zip = ZipOutputStream(dst.outputStream())
    for ((name, data) in files) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }
    zip.close()

}