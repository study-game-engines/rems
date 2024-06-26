package me.anno.io

import me.anno.config.DefaultConfig
import me.anno.ecs.prefab.PrefabCache
import me.anno.extensions.plugins.Plugin
import me.anno.image.thumbs.AssetThumbnails
import me.anno.io.files.Reference.getReference
import me.anno.io.files.inner.InnerFolder
import me.anno.io.files.inner.InnerFolderCache
import me.anno.image.thumbs.Thumbs
import me.anno.image.thumbs.AssetThumbHelper
import me.anno.io.links.LNKReader
import me.anno.io.links.URLReader
import me.anno.io.links.WindowsShortcut
import me.anno.io.unity.UnityReader
import me.anno.io.zip.ExeSkipper
import me.anno.io.zip.Inner7zFile
import me.anno.io.zip.InnerRarFile
import me.anno.io.zip.InnerTarFile
import me.anno.io.zip.InnerZipFile
import java.io.IOException

/**
 * registers all available readers into InnerFolderCache
 * */
class UnpackPlugin : Plugin() {

    override fun onEnable() {
        super.onEnable()

        DefaultConfig.addImportMappings("Asset", *AssetThumbHelper.unityExtensions.toTypedArray())
        PrefabCache.unityReader = UnityReader::loadUnityFile

        // compressed folders
        InnerFolderCache.register(
            listOf("zip", "bz2", "lz4", "xar", "oar"),
            InnerZipFile.Companion::createZipRegistryV2
        )
        InnerFolderCache.register("exe", ExeSkipper::readAsFolder)
        InnerFolderCache.register("7z") { src, callback ->
            val file = Inner7zFile.createZipRegistry7z(src) {
                Inner7zFile.fileFromStream7z(src)
            }
            callback.ok(file)
        }
        InnerFolderCache.register("rar") { src, callback ->
            val file = InnerRarFile.createZipRegistryRar(src) {
                InnerRarFile.fileFromStreamRar(src)
            }
            callback.ok(file)
        }
        InnerFolderCache.register("gzip", InnerTarFile.Companion::readAsGZip)
        InnerFolderCache.register("tar", InnerTarFile.Companion::readAsGZip)

        // Windows and Linux links
        InnerFolderCache.register("lnk", LNKReader::readLNKAsFolder)
        InnerFolderCache.register("url", URLReader::readURLAsFolder)

        Thumbs.registerExtension("lnk") { srcFile, dstFile, size, callback ->
            WindowsShortcut.get(srcFile) { link, exc ->
                if (link != null) {
                    val iconFile = link.iconPath ?: link.absolutePath
                    Thumbs.generate(getReference(iconFile), dstFile, size, callback)
                } else callback.err(exc)
            }
        }

        // try as an asset
        for (ext in AssetThumbHelper.unityExtensions) {
            Thumbs.registerExtension(ext, AssetThumbnails::generateAssetFrame)
        }

        // register yaml generally for unity files?
        InnerFolderCache.registerFileExtension(AssetThumbHelper.unityExtensions) { it, c ->
            val f = UnityReader.readAsFolder(it) as? InnerFolder
            c.call(f, if (f == null) IOException("$it cannot be read as Unity project") else null)
        }
    }

    override fun onDisable() {
        super.onDisable()
        for (sig in listOf("zip", "bz2", "lz4", "xar", "oar", "7z", "rar", "gzip", "tar", "url")) {
            InnerFolderCache.unregisterSignatures(sig)
        }
        // unregister more?
        PrefabCache.unityReader = null
    }
}