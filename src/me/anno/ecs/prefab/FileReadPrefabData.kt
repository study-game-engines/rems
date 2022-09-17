package me.anno.ecs.prefab

import me.anno.cache.AsyncCacheData
import me.anno.cache.ICacheData
import me.anno.io.ISaveable
import me.anno.io.files.FileReference
import me.anno.io.files.FileWatch

class FileReadPrefabData(val reference: FileReference) : AsyncCacheData<ISaveable>() {

    val prefab get() = value as? Prefab
    val instance
        get(): ISaveable? {
            val value = value
            return if (value is Prefab) null else value
        }

    override fun destroy() {
        super.destroy()
        FileWatch.removeWatchDog(reference)
    }
}