package me.anno.engine.ui

import me.anno.ecs.prefab.Prefab
import me.anno.ecs.prefab.PrefabCache
import me.anno.engine.EngineBase
import me.anno.gpu.GFX
import me.anno.image.ImageReadable
import me.anno.image.thumbs.Thumbs
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.files.LastModifiedCache
import me.anno.io.files.Signature
import me.anno.io.files.inner.InnerFolder
import me.anno.io.files.inner.temporary.InnerTmpWriteFile
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.ui.editor.files.FileExplorer
import me.anno.ui.editor.files.FileNames.toAllowedFilename
import me.anno.utils.OS
import me.anno.utils.files.Files
import org.apache.logging.log4j.LogManager

object AssetImport {

    private val LOGGER = LogManager.getLogger(AssetImport::class)

    fun interface PrefabCopier {
        fun copy(srcFile: FileReference, dstFolder: FileReference, srcPrefab: Prefab, depth: Int): Prefab
    }

    fun shallowCopyImport(dst: FileReference, files: List<FileReference>, fe: FileExplorer?) {
        val progress = GFX.someWindow.addProgressBar("Shallow Copy", "", files.size.toDouble())
        for (src in files) {
            val srcPrefab = PrefabCache[src]
            if (srcPrefab == null) {
                LOGGER.warn("Cannot read $src as prefab")
                continue
            }
            progress.total += 0.5
            val dstPrefab = Prefab(srcPrefab.clazzName, src)
            val name = findName(src, dstPrefab, true)
            savePrefab(dst, name, dstPrefab)
            progress.total += 0.5
        }
        onCopyFinished(dst, fe)
        progress.finish(true)
    }

    fun deepCopyImport(dst: FileReference, files: List<FileReference>, fe: FileExplorer?) {
        val progress = GFX.someWindow.addProgressBar("Deep Copy", "", files.size.toDouble())
        val mapping = HashMap<FileReference, FileReference>()
        for (src in files) {
            deepCopyAssets(src, dst, mapping)
            progress.total += 1.0
        }
        onCopyFinished(dst, fe)
        progress.finish(true)
    }

    fun onCopyFinished(dst: FileReference, fe: FileExplorer?) {
        fe?.invalidate()
        LastModifiedCache.invalidate(dst)
        // update icon of current folder... hopefully this works
        Thumbs.invalidate(dst)
        Thumbs.invalidate(dst.getParent())
    }

    fun getPureTypeOrNull(file: FileReference): String? {
        val alias = (file as? InnerFolder)?.alias
        if (alias != null) return getPureTypeOrNull(alias)
        return if (file.isDirectory) null else {
            when (Signature.findNameSync(file)) {
                // todo add all pure-copy extensions
                "jpg", "png", "bmp", "svg", "gif", "qoi", "media", "hdr", "exr", "dds", "webp", "gimp" -> "Image"
                "txt" -> "Text"
                "pdf" -> "Document"
                "woff", "woff2" -> "Font"
                // maybe list the negatives?
                else -> null
            }
        }
    }

    private fun generalCopyAssets(
        srcFolder: FileReference, dstFolder: FileReference, dstFolder0: FileReference,
        prefabMapping: HashMap<FileReference, FileReference>, depth: Int,
        copier: PrefabCopier
    ) {
        // if asset is pure (png,jpg,...), just copy or link it
        if (getPureTypeOrNull(srcFolder) != null) {
            val dstFile = if (dstFolder.isDirectory) {
                dstFolder.getChild(srcFolder.name)
            } else dstFolder
            dstFile.writeFile(srcFolder) {}
        } else {
            for (srcFile in srcFolder.listChildren()) {
                val type = getPureTypeOrNull(srcFile)
                if (type != null) {
                    copyPureFile(srcFile, getDstFolder(dstFolder, dstFolder0, type), prefabMapping)
                } else if (srcFile.isDirectory) {
                    val dstFolder2 = dstFolder.getChild(srcFile.name)
                    if (!dstFolder2.exists) dstFolder2.tryMkdirs()
                    if (dstFolder2.isDirectory) {
                        generalCopyAssets(srcFile, dstFolder2, dstFolder0, prefabMapping, depth + 1, copier)
                    } else LOGGER.warn("Could not create directory $dstFolder2 for $srcFile/${srcFile.name}")
                } else {

                    // read as inner folder
                    // rename its inner "Scene.json" into src.nameWithoutExtension + .json
                    // import all other inner files into their respective files
                    // if content is identical, we could merge them

                    // it may not be necessarily a prefab, it could be a saveable
                    val prefab = loadPrefab(srcFile)
                    if (prefab != null) {
                        val name = findName(srcFile, prefab, dstFolder == dstFolder0)
                        val newPrefab = copier.copy(srcFile, dstFolder, prefab, depth)
                        val dstFile = savePrefab(dstFolder, name, newPrefab)
                        newPrefab.source = dstFile
                        prefabMapping[srcFile] = dstFile
                    } else LOGGER.warn("Skipped $srcFile, because it was not a prefab")
                }
            }
        }
    }

    private fun getDstFolder(
        dstFolder: FileReference,
        dstFolder0: FileReference,
        type: String
    ): FileReference {
        val isMainFolder = dstFolder0 == dstFolder
        return if (isMainFolder && type == "Image") dstFolder0.getChild("textures")
        else dstFolder
    }

    private fun copyPureFile(
        srcFile: FileReference,
        dstFolder: FileReference,
        prefabMapping: HashMap<FileReference, FileReference>
    ): FileReference {
        var ext = srcFile.lcExtension
        val bytes = if (srcFile is ImageReadable) {
            ext = srcFile.getParent().lcExtension.ifEmpty { srcFile.lcExtension }
            val tmp = InnerTmpWriteFile("tmp.$ext")
            srcFile.readCPUImage().write(tmp)
            tmp.written.toByteArray()
        } else srcFile.readBytesSync()
        val dstFile = saveContent(
            dstFolder,
            srcFile.nameWithoutExtension,
            ext, bytes
        )
        prefabMapping[srcFile] = dstFile
        return dstFile
    }

    private fun copyPrefab(
        srcFile: FileReference, dstFolder: FileReference, dstFolder0: FileReference,
        cache: HashMap<FileReference, FileReference>, depth: Int, copier: PrefabCopier
    ): FileReference {
        if (srcFile == InvalidRef) return InvalidRef
        val cached = cache[srcFile]
        if (cached != null) return cached
        val type = getPureTypeOrNull(srcFile)
        return if (type != null) {
            copyPureFile(srcFile, getDstFolder(dstFolder, dstFolder0, type), cache)
        } else {
            val prefab = loadPrefab(srcFile)
            if (prefab != null) {
                val name = findName(srcFile, prefab, dstFolder == dstFolder0)
                val newPrefab = copier.copy(srcFile, dstFolder, prefab, depth + 1)
                val dstFile = savePrefab(newPrefab.source.ifUndefined(dstFolder), name, newPrefab)
                newPrefab.source = dstFile
                cache[srcFile] = newPrefab.source
                dstFile
            } else {
                LOGGER.warn("Skipped $srcFile, because it was not a prefab")
                InvalidRef
            }
        }
    }

    private fun deepCopyAssets(
        srcFolder: FileReference,
        dstFolder0: FileReference,
        prefabMapping: HashMap<FileReference, FileReference>
    ) {
        // todo deep copy is unable to copy reference loops...
        val copier = object : PrefabCopier {
            override fun copy(srcFile: FileReference, dstFolder: FileReference, srcPrefab: Prefab, depth: Int): Prefab {
                // find all referenced files, and copy them, too
                val dstPrefab = Prefab(srcPrefab.clazzName)
                var dstFolder1 = dstFolder
                when (srcPrefab.clazzName) {
                    "Mesh" -> dstFolder1 = dstFolder0.getChild("meshes")
                    "Material" -> dstFolder1 = dstFolder0.getChild("materials")
                    "Animation" -> dstFolder1 = dstFolder0.getChild("animations")
                    "Skeleton" -> dstFolder1 = dstFolder0.getChild("skeletons")
                }
                if (dstFolder1 !== dstFolder) {
                    dstFolder1.tryMkdirs()
                }
                dstPrefab.source = dstFolder1
                if (depth < 10) {
                    dstPrefab.prefab = copyPrefab(
                        srcPrefab.prefab, dstFolder1, dstFolder0,
                        prefabMapping, depth, this
                    )
                    for ((_, addsI) in srcPrefab.adds) {
                        for (add in addsI) {
                            val clone = add.clone()
                            clone.prefab = copyPrefab(
                                add.prefab, dstFolder0, dstFolder0,
                                prefabMapping, depth, this
                            )
                            dstPrefab.add(clone, -1)
                        }
                    }
                    srcPrefab.sets.forEach { k1, k2, value0 ->
                        var value = value0
                        // todo we need to also inspect AnimationState, and structures like it
                        if (value is FileReference) {
                            value = copyPrefab(
                                value, dstFolder0, dstFolder0,
                                prefabMapping, depth, this
                            )
                        } else if (value is List<*> && value.any { it is FileReference }) {
                            value = value.map {
                                if (it is FileReference) copyPrefab(
                                    it, dstFolder0, dstFolder0,
                                    prefabMapping, depth, this
                                )
                                else it
                            }
                        }
                        dstPrefab.sets[k1, k2] = value
                    }
                } else LOGGER.warn("Cannot resolve deep-links in $srcFile because of recursion")
                return dstPrefab
            }
        }
        generalCopyAssets(srcFolder, dstFolder0, dstFolder0, prefabMapping, 0, copier)
    }

    private fun loadPrefab(srcFile: FileReference): Prefab? {
        if (srcFile.lcExtension == "jpg") {
            throw IllegalStateException("Requesting prefab for binary file??? $srcFile")
        }
        return try {
            PrefabCache[srcFile]
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findName(srcFile: FileReference, prefab: Prefab?, isMainFolder: Boolean): String {
        val prefabName = prefab?.instanceName?.toAllowedFilename()
        val fileName = srcFile.nameWithoutExtension.toAllowedFilename()
        var name = fileName ?: srcFile.getParent().nameWithoutExtension
        if (name.toIntOrNull() != null) {
            name = prefabName ?: "Scene"
        }
        if (isMainFolder && name == "Scene") {
            // rename to file name
            name = srcFile.getParent().nameWithoutExtension
        }
        return name
    }

    private fun findNameWithExt(srcFile: FileReference, isMainFolder: Boolean): String {
        val fileName = srcFile.name.toAllowedFilename()
        var name = fileName ?: srcFile.getParent().name
        if (name.toIntOrNull() != null) {
            name = "Scene"
        }
        if (isMainFolder && name == "Scene") {
            // rename to file name
            name = srcFile.getParent().name
        }
        if ('.' !in name) {
            val signature = Signature.findNameSync(srcFile)
            val extName = if (signature == "media") null else signature
            if (extName != null) {
                name = "$name.$extName"
            }
        }
        return name
    }

    private fun savePrefab(dstFolder: FileReference, name: String, newPrefab: Prefab): FileReference {
        val data = JsonStringWriter.toText(newPrefab, EngineBase.workspace).encodeToByteArray()
        return saveContent(dstFolder, name, "json", data)
    }

    private fun saveContent(dstFolder: FileReference, name: String, ext: String, data: ByteArray): FileReference {
        var dstFile = dstFolder.getChild("$name.$ext")
        if (dstFile.exists) {
            // compare the contents: if identical, we can use it
            val data0 = try {
                dstFile.readBytesSync()
            } catch (e: Exception) {
                null
            }
            // todo this comparison can only be done when everything has been renamed... [ShallowCopy]
            // todo we also have the issue, that we should compare with all siblings
            if (!data0.contentEquals(data)) {
                println("Files differ [$name]: ${data0?.hashCode()}[${data0?.size}] vs ${data.hashCode()}[${data.size}]")
                dstFile = Files.findNextFile(dstFolder, name, ext, 3, '-', 1)
            }
        }
        dstFile.getParent().tryMkdirs()
        dstFile.writeBytes(data)
        return dstFile
    }

    fun createLink(original: FileReference, linkLocation: FileReference) {
        val data = if (OS.isWindows) {
            // create .url file
            // are they supported for static files, too???
            if (original.absolutePath.contains("://")) {
                "[InternetShortcut]\n" +
                        "URL=$original\n"
            } else {
                "[InternetShortcut]\n" +
                        "URL=file://$original\n"
            }
        } else {
            // create .desktop file
            // sample data by https://help.ubuntu.com/community/UnityLaunchersAndDesktopFiles:
            "[Desktop Entry]\n" +
                    "Version=1.0\n" +
                    "Name=${original.name}\n" +
                    "Exec=${original.absolutePath}\n" +
                    "Icon=${original.absolutePath}\n" +
                    "Type=Link\n"
        }
        linkLocation.writeText(data)
    }
}