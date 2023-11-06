package me.anno.cache

import me.anno.Build
import me.anno.Time.nanoTime
import me.anno.cache.instances.LastModifiedCache
import me.anno.ecs.components.anim.AnimationCache
import me.anno.ecs.components.anim.SkeletonCache
import me.anno.ecs.components.mesh.MaterialCache
import me.anno.ecs.components.mesh.MeshCache
import me.anno.gpu.GFX
import me.anno.io.files.FileReference
import me.anno.utils.ShutdownException
import me.anno.utils.hpc.ProcessingQueue
import me.anno.utils.structures.maps.KeyPairMap
import me.anno.utils.structures.maps.Maps
import me.anno.utils.structures.maps.Maps.removeIf2
import org.apache.logging.log4j.LogManager
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

open class CacheSection(val name: String) : Comparable<CacheSection> {

    val cache = HashMap<Any?, CacheEntry>(512)
    val dualCache = KeyPairMap<Any?, Any?, CacheEntry>(512)

    override fun compareTo(other: CacheSection): Int {
        return name.compareTo(other.name)
    }

    fun clear() {
        LOGGER.warn("Clearing cache {}", name)
        synchronized(cache) {
            for (it in cache.values) it.destroy()
            cache.clear()
        }
        synchronized(dualCache) {
            dualCache.forEach { _, _, v -> v.destroy() }
            dualCache.clear()
        }
    }

    inline fun remove(crossinline filter: (Any?, CacheEntry) -> Boolean): Int {
        synchronized(cache) {
            return cache.removeIf2 { k, v ->
                if (filter(k, v)) {
                    v.destroy()
                    true
                } else false
            }
        }
    }

    inline fun removeDual(crossinline filter: (Any?, Any?, CacheEntry) -> Boolean): Int {
        synchronized(dualCache) {
            return dualCache.removeIf { k1, k2, v ->
                if (filter(k1, k2, v)) {
                    v.destroy()
                    true
                } else false
            }
        }
    }

    fun removeEntry(key: Any): ICacheData? {
        synchronized(cache) {
            val v = cache.remove(key)
            v?.destroy()
            return v?.data
        }
    }

    fun removeEntry(key1: Any, key2: Any) {
        synchronized(dualCache) {
            dualCache.removeIf { k1, k2, v ->
                if (k1 == key1 && k2 == key2) {
                    v.destroy()
                    true
                } else false
            }
        }
    }

    fun removeFileEntry(file: FileReference) = removeEntry(file, file.lastModified)

    fun getFileEntry(
        file: FileReference, allowDirectories: Boolean,
        timeout: Long, asyncGenerator: Boolean,
        generator: (FileReference, Long) -> ICacheData?
    ): ICacheData? {
        if (!file.exists || (!allowDirectories && file.isDirectory)) return null
        return getDualEntry(file, file.lastModified, timeout, asyncGenerator, generator)
    }

    fun <V> getEntry(
        file: FileReference, allowDirectories: Boolean,
        key: V, timeout: Long, asyncGenerator: Boolean,
        generator: (FileReference, V) -> ICacheData?
    ): ICacheData? {
        if (!file.exists || (!allowDirectories && file.isDirectory)) return null
        return getDualEntry(file, key, timeout, asyncGenerator, generator)
    }

    /**
     * get the value, without generating it if it doesn't exist;
     * delta is added to its timeout, when necessary, so it stays loaded
     * */
    fun getEntryWithoutGenerator(key: Any?, delta: Long = 1L): ICacheData? {
        val entry = synchronized(cache) { cache[key] } ?: return null
        if (delta > 0L) entry.update(delta)
        return entry.data
    }

    /**
     * get the value, without generating it if it doesn't exist;
     * delta is added to its timeout, when necessary, so it stays loaded
     * */
    fun getDualEntryWithoutGenerator(key1: Any, key2: Any, delta: Long = 1L): ICacheData? {
        val entry = synchronized(dualCache) { dualCache[key1, key2] } ?: return null
        if (delta > 0L) entry.update(delta)
        return entry.data
    }

    /**
     * returns whether a value is present
     * */
    fun hasEntry(key: Any, delta: Long = 1L): Boolean {
        val entry = synchronized(cache) { cache[key] } ?: return false
        if (delta > 0L) entry.update(delta)
        return entry.hasValue
    }

    /**
     * returns whether a value is present
     * */
    fun hasDualEntry(key1: Any, key2: Any, delta: Long = 1L): Boolean {
        val entry = synchronized(dualCache) { dualCache[key1, key2] } ?: return false
        if (delta > 0L) entry.update(delta)
        return entry.hasValue
    }

    /**
     * returns whether a value is present
     * */
    fun hasFileEntry(key: FileReference, delta: Long = 1L): Boolean =
        hasDualEntry(key, key.lastModified, delta)

    fun override(key: Any?, newValue: ICacheData?, timeoutMillis: Long) {
        checkKey(key)
        val oldValue = synchronized(cache) {
            val entry = CacheEntry(timeoutMillis)
            entry.data = newValue
            cache.put(key, entry)
        }
        oldValue?.destroy()
    }

    fun overrideDual(key0: Any?, key1: Any?, newValue: ICacheData?, timeoutMillis: Long) {
        checkKey(key0)
        checkKey(key1)
        val oldValue = synchronized(dualCache) {
            val entry = CacheEntry(timeoutMillis)
            entry.data = newValue
            val oldValue = dualCache[key0, key1]
            dualCache[key0, key1] = entry
            oldValue
        }
        oldValue?.destroy()
    }

    private fun <V> generateSafely(key: V, generator: (V) -> ICacheData?): Any? {
        var data: ICacheData? = null
        try {
            data = generator(key)
        } catch (_: ShutdownException) { // shutting down anyway
        } catch (e: FileNotFoundException) {
            LOGGER.warn("FileNotFoundException: {}", e.message)
        } catch (e: Exception) {
            return e
        }
        return data
    }

    private fun <V, W> generateDualSafely(key0: V, key1: W, generator: (V, W) -> ICacheData?): Any? {
        var data: ICacheData? = null
        try {
            data = generator(key0, key1)
        } catch (_: ShutdownException) { // shutting down anyway
        } catch (e: FileNotFoundException) {
            LOGGER.warn("FileNotFoundException: {}", e.message)
        } catch (e: ShutdownException) {
            throw e
        } catch (e: Exception) {
            return e
        }
        return data
    }

    private fun checkKey(key: Any?) {
        if (Build.isDebug && key != null) {
            @Suppress("KotlinConstantConditions")
            if (key != key) throw IllegalStateException("${key.javaClass.name}.equals() is incorrect!")
            if (key.hashCode() != key.hashCode()) throw IllegalStateException("${key.javaClass.name}.hashCode() is inconsistent!")
        }// else we assume that it's fine
    }

    private val limiter = AtomicInteger()
    fun <V> getEntryLimited(
        key: V, timeout: Long, asyncGenerator: Boolean,
        limit: Int, generator: (V) -> ICacheData
    ): ICacheData? {
        val accessTry = limiter.getAndIncrement()
        return if (accessTry < limit) {
            // we're good to go, and can make our request
            getEntryWithIfNotGeneratingCallback(key, timeout, asyncGenerator, {
                val value = generateSafely(key, generator)
                limiter.decrementAndGet()
                if (value is Exception) throw value
                value as? ICacheData
            }) { limiter.decrementAndGet() }
        } else {
            limiter.decrementAndGet()
            // get the value without generator
            getEntryWithoutGenerator(key, 1L)
        }
    }

    fun <V> getEntryLimited(
        key: V, timeout: Long, queue: ProcessingQueue?,
        limit: Int, generator: (V) -> ICacheData
    ): ICacheData? {
        val accessTry = limiter.getAndIncrement()
        return if (accessTry < limit) {
            // we're good to go, and can make our request
            getEntryWithIfNotGeneratingCallback(key, timeout, queue, {
                val value = generateSafely(key, generator)
                limiter.decrementAndGet()
                if (value is Exception) throw value
                value as? ICacheData
            }) { limiter.decrementAndGet() }
        } else {
            limiter.decrementAndGet()
            // get the value without generator
            getEntryWithoutGenerator(key, 1L)
        }
    }

    fun <V, W> getDualEntryWithIfNotGeneratingCallback(
        key0: V, key1: W,
        timeoutMillis: Long, asyncGenerator: Boolean,
        generator: (key0: V, key1: W) -> ICacheData?,
        ifNotGenerating: (() -> Unit)?
    ): ICacheData? {

        checkKey(key0)
        checkKey(key1)

        // new, async cache
        // only the key needs to be locked, not the whole cache

        val entry = synchronized(dualCache) {
            val entry = dualCache.getOrPut(key0, key1) { _, _ -> CacheEntry(timeoutMillis) }
            if (entry.hasBeenDestroyed) entry.reset(timeoutMillis)
            entry
        }

        val needsGenerator = entry.needsGenerator
        entry.update(timeoutMillis)

        if (needsGenerator) {
            entry.hasGenerator = true
            if (asyncGenerator) {
                val name = "$name<$key0,$key1>"
                LOGGER.debug("Started {}", name)
                thread(name = name) {
                    try {
                        val value = generateDualSafely(key0, key1, generator)
                        entry.data = value as? ICacheData
                        if (value is Exception) throw value
                        if (entry.hasBeenDestroyed) {
                            LOGGER.warn("Value for $name<$key0,$key1> was directly destroyed")
                            entry.data?.destroy()
                        }
                    } catch (ignored: ShutdownException) {
                        // don't care
                    }
                    LOGGER.debug("Finished {}", name)
                }
            } else {
                val value = generateDualSafely(key0, key1, generator)
                entry.data = value as? ICacheData
                if (value is Exception) throw value
            }
        } else ifNotGenerating?.invoke()

        waitMaybe(asyncGenerator, entry, key0, key1)
        return if (entry.hasBeenDestroyed) null else entry.data
    }

    fun <V> getEntryWithIfNotGeneratingCallback(
        key: V, timeoutMillis: Long,
        asyncGenerator: Boolean,
        generator: (V) -> ICacheData?,
        ifNotGenerating: (() -> Unit)?
    ): ICacheData? {

        checkKey(key)

        // new, async cache
        // only the key needs to be locked, not the whole cache

        val entry = synchronized(cache) {
            val entry = cache.getOrPut(key) { CacheEntry(timeoutMillis) }
            if (entry.hasBeenDestroyed) entry.reset(timeoutMillis)
            entry
        }

        val needsGenerator = entry.needsGenerator
        entry.update(timeoutMillis)

        if (needsGenerator) {
            entry.hasGenerator = true
            if (asyncGenerator) {
                val name = "$name<$key>"
                LOGGER.debug("Started {}", name)
                thread(name = name) {
                    val value = generateSafely(key, generator)
                    if (value is Exception && value !is ShutdownException) throw value
                    value as? ICacheData
                    entry.data = value as? ICacheData
                    if (entry.hasBeenDestroyed) {
                        LOGGER.warn("Value for {}<{}> was directly destroyed", name, key)
                        entry.data?.destroy()
                    }
                    LOGGER.debug("Finished {}", name)
                }
            } else {
                val value = generateSafely(key, generator)
                if (value is Exception) throw value
                entry.data = value as? ICacheData
            }
        } else ifNotGenerating?.invoke()

        waitMaybe(asyncGenerator, entry, key, null)
        return if (entry.hasBeenDestroyed) null else entry.data
    }

    fun <V> getEntryAsync(
        key: V, timeoutMillis: Long,
        asyncGenerator: Boolean,
        generator: (V) -> ICacheData?,
        resultCallback: (ICacheData?, Exception?) -> Unit
    ) {

        checkKey(key)

        // new, async cache
        // only the key needs to be locked, not the whole cache

        val entry = synchronized(cache) {
            val entry = cache.getOrPut(key) { CacheEntry(timeoutMillis) }
            if (entry.hasBeenDestroyed) entry.reset(timeoutMillis)
            entry
        }

        val needsGenerator = entry.needsGenerator
        entry.update(timeoutMillis)

        fun callback(exception: Exception?) {
            resultCallback(
                if (entry.hasBeenDestroyed) null
                else entry.data, exception
            )
        }

        if (needsGenerator) {
            entry.hasGenerator = true
            if (asyncGenerator) {
                val name = "$name<$key>"
                LOGGER.debug("Started {}", name)
                thread(name = name) {
                    val value = generateSafely(key, generator)
                    entry.data = value as? ICacheData
                    LOGGER.debug("Finished {}", name)
                    callback(value as? Exception)
                }
            } else {
                val value = generateSafely(key, generator)
                entry.data = value as? ICacheData
                callback(value as? Exception)
            }
        } else {
            if (!entry.hasValue && entry.generatorThread != Thread.currentThread()) {
                thread(name = "WaitingFor<$key>") {
                    entry.waitForValue(key)
                    callback(null)
                }
            } else callback(null)
        }
    }

    fun <V, W> getDualEntryAsync(
        key0: V, key1: W, timeoutMillis: Long,
        asyncGenerator: Boolean,
        generator: (V, W) -> ICacheData?,
        resultCallback: (ICacheData?, Exception?) -> Unit
    ) {

        checkKey(key0)
        checkKey(key1)

        // new, async cache
        // only the key needs to be locked, not the whole cache

        val entry = synchronized(dualCache) {
            val entry = dualCache.getOrPut(key0, key1) { _, _ -> CacheEntry(timeoutMillis) }
            if (entry.hasBeenDestroyed) entry.reset(timeoutMillis)
            entry
        }

        val needsGenerator = entry.needsGenerator
        entry.update(timeoutMillis)

        fun callback(exception: Exception?) {
            resultCallback(
                if (entry.hasBeenDestroyed) null
                else entry.data, exception
            )
        }

        if (needsGenerator) {
            entry.hasGenerator = true
            if (asyncGenerator) {
                val name = "$name<$key0,$key1>"
                LOGGER.debug("Started {}", name)
                thread(name = name) {
                    val value = generateDualSafely(key0, key1, generator)
                    entry.data = value as? ICacheData
                    LOGGER.debug("Finished {}", name)
                    callback(value as? Exception)
                }
            } else {
                val value = generateDualSafely(key0, key1, generator)
                entry.data = value as? ICacheData
                callback(value as? Exception)
            }
        } else {
            if (!entry.hasValue && entry.generatorThread != Thread.currentThread()) {
                thread(name = "WaitingFor<$key0, $key1>") {
                    entry.waitForValue(key0 to key1)
                    callback(null)
                }
            } else callback(null)
        }
    }

    private fun waitMaybe(async: Boolean, entry: CacheEntry, key0: Any?, key1: Any?) {
        if (!async && entry.generatorThread != Thread.currentThread()) {
            // else no need/sense in waiting
            if (entry.waitForValue2()) {
                val key = if (key1 == null) key0 else key0 to key1
                onLongWaitStart(key, entry)
                entry.waitForValue(key)
                onLongWaitEnd(key, entry)
            }
        }
    }

    private fun onLongWaitStart(key: Any?, entry: CacheEntry) {
        val msg = "Waiting for $name[$key] by ${entry.generatorThread.name} " +
                "from ${Thread.currentThread().name}"
        if (Thread.currentThread() == GFX.glThread) println(msg) // extra warning
        LOGGER.warn(msg)
    }

    private fun onLongWaitEnd(key: Any?, entry: CacheEntry) {
        val msg = "Finished waiting for $name[$key] by ${entry.generatorThread.name} " +
                "from ${Thread.currentThread().name}"
        if (Thread.currentThread() == GFX.glThread) println(msg) // extra warning
        LOGGER.warn(msg)
    }

    fun <V> getEntryWithIfNotGeneratingCallback(
        key: V, timeoutMillis: Long,
        queue: ProcessingQueue?,
        generator: (V) -> ICacheData?,
        ifNotGenerating: (() -> Unit)?
    ): ICacheData? {

        if (key == null) throw IllegalStateException("Key must not be null")
        checkKey(key)

        // new, async cache
        // only the key needs to be locked, not the whole cache

        val entry = synchronized(cache) {
            val entry = cache.getOrPut(key) { CacheEntry(timeoutMillis) }
            if (entry.hasBeenDestroyed) entry.reset(timeoutMillis)
            entry
        }

        val needsGenerator = entry.needsGenerator
        entry.update(timeoutMillis)

        val async = queue != null
        if (needsGenerator) {
            entry.hasGenerator = true
            if (queue != null) {
                queue += {
                    val value = generateSafely(key, generator)
                    if (value is Exception) throw value
                    entry.data = value as? ICacheData
                    if (entry.hasBeenDestroyed) {
                        LOGGER.warn("Value for $name<$key> was directly destroyed")
                        entry.data?.destroy()
                    }
                }
            } else {
                val value = generateSafely(key, generator)
                if (value is Exception) throw value
                entry.data = value as? ICacheData
            }
        } else ifNotGenerating?.invoke()

        if (!async && entry.generatorThread != Thread.currentThread()) entry.waitForValue(key)
        return if (entry.hasBeenDestroyed) null else entry.data
    }

    fun <V> getEntry(key: V, timeoutMillis: Long, asyncGenerator: Boolean, generator: (V) -> ICacheData?): ICacheData? =
        getEntryWithIfNotGeneratingCallback(key, timeoutMillis, asyncGenerator, generator, null)

    fun <V, W> getDualEntry(
        key0: V, key1: W, timeoutMillis: Long, asyncGenerator: Boolean,
        generator: (V, W) -> ICacheData?
    ): ICacheData? =
        getDualEntryWithIfNotGeneratingCallback(key0, key1, timeoutMillis, asyncGenerator, generator, null)

    fun <V> getEntry(key: V, timeoutMillis: Long, queue: ProcessingQueue?, generator: (V) -> ICacheData?): ICacheData? =
        getEntryWithIfNotGeneratingCallback(key, timeoutMillis, queue, generator, null)

    fun update() {
        synchronized(cache) {
            // avoiding allocations for clean memory debugging XD
            cache.removeIf2(remover)
        }
        synchronized(dualCache) {
            dualCache.removeIf { _, _, v ->
                if (nanoTime > v.timeoutNanoTime) {
                    v.destroy()
                    true
                } else false
            }
        }
    }

    init {
        caches += this
    }

    companion object {

        @JvmStatic
        private val remover = object : Maps.Remover<Any?, CacheEntry>() {
            override fun filter(key: Any?, value: CacheEntry): Boolean {
                return if (nanoTime > value.timeoutNanoTime) {
                    value.destroy()
                    true
                } else false
            }
        }

        @JvmStatic
        private val caches = ConcurrentSkipListSet<CacheSection>()

        @JvmStatic
        fun updateAll() {
            for (cache in caches) cache.update()
            LastModifiedCache.update()
            MeshCache.update()
            AnimationCache.update()
            MaterialCache.update()
            SkeletonCache.update()
        }

        @JvmStatic
        fun clearAll() {
            for (cache in caches) cache.clear()
            LastModifiedCache.clear()
        }

        fun invalidateFiles(path: String) {
            val filter = { file: Any?, _: Any?, _: Any? ->
                (file is FileReference && file.absolutePath.startsWith(path, true)) ||
                        (file is String && file.startsWith(path, true))
            }
            val removed = ArrayList<Pair<String, Int>>()
            for (cache in caches) {
                val count = cache.removeDual(filter)
                if (count > 0) removed.add(Pair(cache.name, count))
            }
            when (removed.sumOf { it.second }) {
                0 -> {}
                1 -> LOGGER.debug("Removed one file entry from ${removed.first().first} cache")
                else -> LOGGER.debug(
                    "Removed [${removed.joinToString("+") { it.second.toString() }}] file entries from [" +
                            "${removed.joinToString("+") { it.first }}] caches"
                )
            }
        }

        @JvmStatic
        private val LOGGER = LogManager.getLogger(CacheSection::class)
    }
}