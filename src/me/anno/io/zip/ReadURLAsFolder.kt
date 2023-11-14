package me.anno.io.zip

import me.anno.io.files.FileReference
import me.anno.io.files.inner.InnerFolder
import me.anno.io.files.inner.InnerLinkFile
import me.anno.utils.files.LocalFile.toGlobalFile
import java.io.IOException

fun readURLAsFolder(file: FileReference, callback: (InnerFolder?, Exception?) -> Unit) {
    file.readLines(1024) { lines, exception ->
        if (lines == null) callback(null, exception)
        else {
            val files = ArrayList<FileReference>()
            for (line in lines) {
                if (line.startsWith("URL=file://")) {
                    files.add(FileReference.Companion.getReference(line.substring(11).toGlobalFile()))
                } else if (line.startsWith("URL=")) {
                    files.add(FileReference.getReference(line.substring(4)))
                }
            }
            lines.close()
            if (files.isNotEmpty()) {
                val folder = InnerFolder(file)
                var j = 0
                for (i in files.indices) {
                    val child = files[i]
                    var key = child.name
                    // ensure unique name
                    if (key in folder.children) {
                        var ext = file.extension
                        if (ext.isNotEmpty()) ext = ".$ext"
                        key = "${j++}$ext"
                        while (key in folder.children) {
                            key = "${j++}$ext"
                        }
                    }
                    // create child & add it
                    InnerLinkFile(folder, key, child)
                }
                callback(folder, null)
            } else callback(null, IOException("No files were found in $file"))
        }
    }
}