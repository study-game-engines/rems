package me.anno.ecs.prefab

import me.anno.cache.CacheData
import me.anno.cache.CacheSection
import me.anno.engine.scene.ScenePrefab
import me.anno.io.ISaveable
import me.anno.io.NamedSaveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.files.Signature
import me.anno.io.text.TextReader
import me.anno.io.text.TextWriter
import me.anno.io.unity.UnityReader
import me.anno.mesh.assimp.AnimatedMeshesLoader
import me.anno.mesh.vox.VOXReader
import me.anno.utils.files.LocalFile.toGlobalFile
import org.apache.logging.log4j.LogManager

class Prefab() : NamedSaveable() {

    constructor(clazzName: String) : this() {
        this.clazzName = clazzName
    }

    constructor(clazzName: String, prefab: FileReference) : this(clazzName) {
        this.prefab = prefab
    }

    var clazzName: String? = null

    var changes: List<Change>? = null
    var prefab: FileReference = InvalidRef
    var wasCreatedFromJson = false
    var src: FileReference = InvalidRef

    fun getPrefabOrSource() = prefab.nullIfUndefined() ?: src

    // for the game runtime, we could save the prefab instance here
    // or maybe even just add the changes, and merge them
    // (we don't need to override twice or more times)

    var history: ChangeHistory? = null
    var isValid = false

    fun add(change: Change) {
        if (changes == null) changes = ArrayList()
        (changes as MutableList<Change>).add(change)
        isValid = false
    }

    fun setProperty(key: String, value: Any?) {
        if (changes == null) changes = ArrayList()
        (changes as MutableList<Change>).apply {
            removeIf { it.path!!.isEmpty() && it is CSet && it.name == key }
            add(CSet(Path(), key, value))
        }
        isValid = false
    }

    var sampleInstance: PrefabSaveable? = null

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("prefab", prefab)
        writer.writeString("className", clazzName)
        writer.writeObjectList(null, "changes", changes ?: emptyList())
        writer.writeObject(null, "history", history)
    }

    override fun readString(name: String, value: String?) {
        when (name) {
            "prefab" -> prefab = value?.toGlobalFile() ?: InvalidRef
            "className" -> clazzName = value
            else -> super.readString(name, value)
        }
    }

    override fun readFile(name: String, value: FileReference) {
        when (name) {
            "prefab" -> prefab = value
            else -> super.readFile(name, value)
        }
    }

    override fun readObjectArray(name: String, values: Array<ISaveable?>) {
        when (name) {
            "changes" -> changes = values.filterIsInstance<Change>()
            else -> super.readObjectArray(name, values)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "history" -> history = value as? ChangeHistory ?: return
            else -> super.readObject(name, value)
        }
    }

    private fun createInstance0(chain: MutableSet<FileReference>?): PrefabSaveable {
        isValid = true
        // LOGGER.info("Requesting instance '$src' extends '$prefab'")
        // LOGGER.info("$prefab/${changes?.size}/$clazzName")
        val instance = createInstance(prefab, changes, chain, clazzName!!)
        // assign super instance? we should really cache that...
        instance.prefab2 = this
        return instance
    }

    fun createInstance(): PrefabSaveable = createInstance(HashSet())

    fun createInstance(chain: MutableSet<FileReference>?): PrefabSaveable {
        synchronized(this) {
            if (!isValid) sampleInstance = createInstance0(chain)
        }
        return sampleInstance!!.clone()
    }

    override val className: String = "Prefab"
    override val approxSize: Int = 100_000_000

    override fun isDefaultValue(): Boolean =
        (changes == null || changes!!.isEmpty()) && prefab == InvalidRef && history == null

    companion object {

        val cache = CacheSection("Prefab")
        val prefabTimeout = 60_000L

        private val LOGGER = LogManager.getLogger(Prefab::class)

        private fun createInstance(
            superPrefab: FileReference,
            changes: List<Change>?,
            chain: MutableSet<FileReference>?,
            clazz: String
        ): PrefabSaveable {
            // LOGGER.info("creating instance from $superPrefab")
            val instance = createSuperInstance(superPrefab, chain, clazz)
            // val changes2 = (changes0 ?: emptyList()).groupBy { it.className }.map { "${it.value.size}x ${it.key}" }
            // LOGGER.info("  creating entity instance from ${changes0?.size ?: 0} changes, $changes2")
            if (changes != null) {
                for ((index, change) in changes.withIndex()) {
                    try {
                        change.apply(instance)
                    } catch (e: Exception) {
                        LOGGER.warn("Change $index, $change failed")
                        throw e
                    }
                }
            }
            // LOGGER.info("  created instance '${entity.name}' has ${entity.children.size} children and ${entity.components.size} components")
            return instance
        }

        private fun loadAssimpModel(resource: FileReference): Prefab? {
            return try {
                val reader = AnimatedMeshesLoader
                val meshes = reader.readAsFolder2(resource)
                meshes.second
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun loadVOXModel(resource: FileReference): Prefab? {
            return try {
                VOXReader().read(resource).toEntityPrefab(resource)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun loadJson(resource: FileReference): Prefab? {
            if (resource == InvalidRef) return null
            return try {
                val read = TextReader.read(resource)
                val prefab = read.firstOrNull() as? Prefab
                if (prefab == null) LOGGER.warn("No Prefab found in $resource! $read")
                else LOGGER.info("Read ${prefab.changes?.size} changes from $resource")
                prefab?.wasCreatedFromJson = true
                prefab
            } catch (e: Exception) {
                LOGGER.warn("$e by $resource")
                e.printStackTrace()
                null
            }
        }

        fun loadUnityFile(resource: FileReference): Prefab? {
            return loadJson(UnityReader.readAsAsset(resource))
        }

        fun loadPrefab(resource: FileReference): Prefab? {
            return if (resource != InvalidRef && resource.exists && !resource.isDirectory) {
                val data = cache.getEntry(resource, prefabTimeout, false) {
                    CacheData(
                        if (resource.exists && !resource.isDirectory) {
                            val signature = Signature.find(resource)
                            // LOGGER.info("resource $resource has signature $signature")
                            when (signature?.name) {
                                "vox" -> loadVOXModel(resource)
                                "fbx", "obj", "gltf" -> loadAssimpModel(resource)
                                "yaml" -> loadUnityFile(resource)
                                "json" -> loadJson(resource)
                                else -> {
                                    when (resource.extension.lowercase()) {
                                        "vox" -> loadVOXModel(resource)
                                        "fbx", "dae", "obj", "gltf", "glb" -> loadAssimpModel(resource)
                                        "unity", "mat", "prefab", "asset", "meta", "controller" -> loadUnityFile(
                                            resource
                                        )
                                        // todo define file extensions for materials, skeletons, components
                                        else -> loadJson(resource)
                                    }
                                }
                            }?.apply { src = resource }
                        } else null
                    )
                } as CacheData<*>
                return data.value as? Prefab
            } else null
        }

        private fun createSuperInstance(
            prefab: FileReference,
            chain: MutableSet<FileReference>?,
            clazz: String
        ): PrefabSaveable {
            if (chain != null) {
                if (prefab in chain) throw RuntimeException("Hit dependency ring: $chain, $prefab")
                chain.add(prefab)
            }
            // LOGGER.info("chain: $chain")
            return loadPrefab(prefab)?.createInstance(chain) ?: ISaveable.create(clazz) as PrefabSaveable
        }

        fun loadScenePrefab(file: FileReference): Prefab {
            // LOGGER.info("loading scene")
            val prefab = loadPrefab(file) ?: Prefab("Entity").apply { this.prefab = ScenePrefab }
            prefab.src = file
            if (!file.exists) file.writeText(TextWriter.toText(prefab, false))
            return prefab
        }

    }

}