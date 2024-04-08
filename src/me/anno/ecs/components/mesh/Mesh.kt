package me.anno.ecs.components.mesh

import me.anno.cache.ICacheData
import me.anno.ecs.Entity
import me.anno.ecs.annotations.DebugAction
import me.anno.ecs.annotations.DebugProperty
import me.anno.ecs.annotations.Docs
import me.anno.ecs.annotations.HideInInspector
import me.anno.ecs.annotations.Type
import me.anno.ecs.components.mesh.callbacks.LineIndexCallback
import me.anno.ecs.components.mesh.callbacks.TriangleIndexCallback
import me.anno.ecs.components.mesh.utils.MorphTarget
import me.anno.ecs.components.mesh.utils.NormalCalculator
import me.anno.ecs.components.mesh.utils.TangentCalculator
import me.anno.ecs.interfaces.Renderable
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.ecs.prefab.change.Path
import me.anno.engine.serialization.NotSerializedProperty
import me.anno.engine.serialization.SerializedProperty
import me.anno.engine.ui.render.RenderMode
import me.anno.engine.ui.render.RenderView
import me.anno.gpu.CullMode
import me.anno.gpu.GFX
import me.anno.gpu.buffer.Attribute
import me.anno.gpu.buffer.Attribute.Companion.computeOffsets
import me.anno.gpu.buffer.AttributeType
import me.anno.gpu.buffer.Buffer
import me.anno.gpu.buffer.DrawMode
import me.anno.gpu.buffer.IndexBuffer
import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.pipeline.Pipeline
import me.anno.gpu.shader.Shader
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.inner.temporary.InnerTmpPrefabFile
import me.anno.utils.types.Booleans.hasFlag
import me.anno.maths.bvh.BLASNode
import me.anno.mesh.FindLines
import me.anno.utils.Color.a
import me.anno.utils.Color.b
import me.anno.utils.Color.g
import me.anno.utils.Color.r
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.lists.Lists.createArrayList
import me.anno.utils.structures.tuples.IntPair
import me.anno.utils.types.Arrays.resize
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import org.joml.AABBf
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// open, so you can define your own attributes
open class Mesh : PrefabSaveable(), IMesh, Renderable, ICacheData {

    // a single helper mesh could be used to represent the default indices...
    class HelperMesh(val indices: IntArray) : ICacheData {

        var triBuffer: IndexBuffer? = null
        var lineBuffer: IndexBuffer? = null
        var debugLineBuffer: IndexBuffer? = null
        var debugLineIndices: IntArray? = null
        var invalidDebugLines = true
        var lineIndices: IntArray? = null

        fun init(mesh: Mesh) {
            val buffer = mesh.buffer!!
            triBuffer = IndexBuffer("helper", buffer, indices)
            triBuffer?.drawMode = mesh.drawMode

            lineIndices = lineIndices ?: FindLines.findLines(mesh, indices, mesh.positions)
            lineBuffer = replaceBuffer(buffer, lineIndices, lineBuffer)
            lineBuffer?.drawMode = DrawMode.LINES
        }

        fun ensureDebugLines(mesh: Mesh) {
            val buffer = mesh.buffer
            if (invalidDebugLines && buffer != null) {
                invalidDebugLines = false
                debugLineIndices = FindLines.getAllLines(mesh, indices)
                debugLineBuffer = replaceBuffer(buffer, debugLineIndices, debugLineBuffer)
                debugLineBuffer?.drawMode = DrawMode.LINES
            }
        }

        override fun destroy() {
            triBuffer?.destroy()
            lineBuffer?.destroy()
            debugLineBuffer?.destroy()
        }
    }

    @NotSerializedProperty
    var raycaster: BLASNode? = null

    // use buffers instead, so they can be uploaded directly? no, I like the strided thing...
    // but it may be more flexible... still in Java, FloatArrays are more comfortable
    // -> just use multiple setters for convenience

    @NotSerializedProperty
    private var needsMeshUpdate = true

    @NotSerializedProperty
    private var needsBoundsUpdate = true

    override var cullMode = CullMode.FRONT

    /**
     * call this function, when you have changed the geometry;
     * on the next frame, the new mesh data will be uploaded to the GPU
     * */
    fun invalidateGeometry() {
        needsMeshUpdate = true
        needsBoundsUpdate = true
        raycaster = null
        val ref = refOrNull
        if (ref is InnerTmpPrefabFile) {
            ref.markAsModified()
        }
    }

    // todo also we need a renderer, which can handle morphing
    // todo or we need to compute it on the cpu

    override var proceduralLength = 0

    var inverseOutline = false

    @HideInInspector
    var positions: FloatArray? = null

    @Type("List<MorphTarget>")
    var morphTargets: List<MorphTarget> = emptyList()

    @Docs("Normals in local space, will be generated automatically if missing")
    @Type("FloatArray?")
    @HideInInspector
    var normals: FloatArray? = null

    @Type("FloatArray?")
    @HideInInspector
    var tangents: FloatArray? = null

    @Type("FloatArray?")
    @HideInInspector
    var uvs: FloatArray? = null

    // colors, rgba,
    // the default shader only supports the first color
    // other colors still can be loaded for ... idk... maybe terrain information or sth like that
    @Type("IntArray?")
    @HideInInspector
    var color0: IntArray? = null

    @Type("IntArray?")
    @HideInInspector
    var color1: IntArray? = null

    @Type("IntArray?")
    @HideInInspector
    var color2: IntArray? = null

    @Type("IntArray?")
    @HideInInspector
    var color3: IntArray? = null

    @Type("IntArray?")
    @HideInInspector
    var color4: IntArray? = null

    @Type("IntArray?")
    @HideInInspector
    var color5: IntArray? = null

    @Type("IntArray?")
    @HideInInspector
    var color6: IntArray? = null

    @Type("IntArray?")
    @HideInInspector
    var color7: IntArray? = null

    @Type("FloatArray?")
    @HideInInspector
    var boneWeights: FloatArray? = null

    @Type("ByteArray?")
    @HideInInspector
    var boneIndices: ByteArray? = null

    val hasBones get() = boneIndices?.isEmpty() == false

    @HideInInspector
    @NotSerializedProperty
    var debugLineIndices: IntArray? = null

    @Docs("Automatically found lines (for rendering); can be set manually")
    @NotSerializedProperty
    var lineIndices: IntArray? = null

    @HideInInspector
    var indices: IntArray? = null

    @SerializedProperty
    @Type("List<Material/Reference>")
    override var materials: List<FileReference> = defaultMaterials

    @HideInInspector
    @NotSerializedProperty
    var material: FileReference?
        get() = materials.firstOrNull()
        set(value) {
            materials = if (value != null) listOf(value)
            else emptyList()
        }

    /**
     * one index per triangle
     * */
    @Type("IntArray?")
    @HideInInspector
    var materialIds: IntArray? = null

    override var numMaterials = 1

    @NotSerializedProperty
    var helperMeshes: List<HelperMesh?>? = null

    // to allow for quads, and strips and such
    /**
     * how the positions / indices are drawn;
     * implementations might only support GL_TRIANGLES, so be careful, and always prefer GL_TRIANGLES!
     * */
    var drawMode = DrawMode.TRIANGLES

    @DebugProperty // todo make this assignable to get-functions
    private val debugBounds get() = getBounds()
    private val aabb = AABBf()

    var ignoreStrayPointsInAABB = false

    override fun clone(): PrefabSaveable {
        val clone = Mesh()
        copyInto(clone)
        return clone
    }

    fun unlink() {
        buffer = null
        triBuffer = null
        lineBuffer = null
        debugLineBuffer = null
        prefabPath = Path.ROOT_PATH
        prefab = null
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as Mesh
        // ensureBuffer()
        // materials
        dst.materials = materials
        // mesh data
        dst.positions = positions
        dst.normals = normals
        dst.uvs = uvs
        dst.color0 = color0
        dst.color1 = color1
        dst.color2 = color2
        dst.color3 = color3
        dst.color4 = color4
        dst.color5 = color5
        dst.color6 = color6
        dst.color7 = color7
        dst.tangents = tangents
        dst.indices = indices
        dst.lineIndices = lineIndices
        dst.boneWeights = boneWeights
        dst.boneIndices = boneIndices
        dst.materialIds = materialIds
        // morph targets
        dst.morphTargets = morphTargets
        // draw mode
        dst.drawMode = drawMode
        dst.cullMode = cullMode
        // buffer
        dst.needsMeshUpdate = needsMeshUpdate
        dst.buffer = buffer
        dst.triBuffer = triBuffer
        dst.lineBuffer = lineBuffer
        dst.debugLineBuffer = debugLineBuffer
        dst.hasUVs = hasUVs
        dst.hasVertexColors = hasVertexColors
        dst.hasBonesInBuffer = hasBonesInBuffer
        dst.helperMeshes = helperMeshes
        dst.hasHighPrecisionNormals = hasHighPrecisionNormals
        // aabb
        dst.aabb.set(aabb)
        dst.ignoreStrayPointsInAABB = ignoreStrayPointsInAABB
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        saveSerializableProperties(writer)
    }

    override fun setProperty(name: String, value: Any?) {
        if (!readSerializableProperty(name, value)) {
            super.setProperty(name, value)
        }
    }

    override val approxSize get() = 1

    fun calculateAABB() {
        aabb.clear()
        val indices = indices
        // if the indices array is empty, it indicates a non-indexed array, so all values will be considered
        val positions = positions ?: return
        if (ignoreStrayPointsInAABB && indices != null && indices.isNotEmpty()) {
            for (i in indices.indices) {
                val i3 = indices[i] * 3
                val x = positions[i3]
                val y = positions[i3 + 1]
                val z = positions[i3 + 2]
                aabb.union(x, y, z)
            }
        } else {
            for (index in 0 until positions.size - 2 step 3) {
                val x = positions[index]
                val y = positions[index + 1]
                val z = positions[index + 2]
                aabb.union(x, y, z)
            }
        }
        // LOGGER.info("Collected aabb $aabb from ${positions?.size}/${indices?.size} points")
    }

    @Suppress("unused")
    fun calculateNormals(smooth: Boolean) {
        val positions = positions!!
        if (smooth && indices == null) {
            indices = NormalCalculator.generateIndices(positions, uvs, color0, materialIds, boneIndices, boneWeights)
            LOGGER.debug("Generated indices for mesh")
        }
        normals = FloatArray(positions.size)
        NormalCalculator.checkNormals(this, positions, normals!!, indices, drawMode)
    }

    /**
     * throws an IllegalStateException, if anything is incorrectly set up
     * if this succeeds, then the drawing routine should not crash
     * */
    fun checkCompleteness() {
        if (proceduralLength > 0) return
        // check whether all variables are set correctly
        val positions = positions
        val normals = normals
        val uvs = uvs
        if (positions == null) throw IllegalStateException("Missing positions, normals? ${normals?.size}, uvs? ${uvs?.size}")
        if (positions.size % 3 != 0) throw IllegalStateException("Positions must be a vector of vec3, but ${positions.size} % 3 != 0, it's ${positions.size % 3}")
        // while incorrect, the following should not cause an exception
        /*if (normals != null && normals.size != positions.size) throw IllegalStateException("Size of normals doesn't match size of positions")
        if (uvs != null) {
            if (uvs.size * 3 != positions.size * 2) throw IllegalStateException("Size of UVs does not match size of positions: ${positions.size}*2 vs ${uvs.size}*3")
        }
        val boneWeights = boneWeights
        val boneIndices = boneIndices
        if ((boneIndices == null) != (boneWeights == null)) throw IllegalStateException("Needs both or neither bone weights and indices")
        if (boneWeights != null && boneIndices != null) {
            if (boneWeights.size != boneIndices.size)
                throw IllegalStateException("Size of bone weights must match size of bone indices, ${boneWeights.size} vs ${boneIndices.size}")
            if (boneWeights.size * 3 != positions.size * MAX_WEIGHTS)
                throw IllegalStateException(
                    "Size of weights does not match positions, there must be $MAX_WEIGHTS weights per vertex, " +
                            "${boneWeights.size} * 3 vs ${positions.size} * $MAX_WEIGHTS"
                )
        }
        val color0 = color0
        if (color0 != null && color0.size * 3 != positions.size) throw IllegalStateException("Every vertex needs an ARGB color value")*/
        val indices = indices
        if (indices != null) {
            // check all indices for correctness
            val vertexCount = positions.size / 3
            for (i in indices) {
                if (i !in 0 until vertexCount) {
                    throw IllegalStateException("Vertex Index is out of bounds: $i !in 0 until $vertexCount")
                }
            }
        }
    }

    @NotSerializedProperty
    var buffer: StaticBuffer? = null

    @NotSerializedProperty
    var triBuffer: IndexBuffer? = null

    @NotSerializedProperty
    var lineBuffer: IndexBuffer? = null

    @NotSerializedProperty
    var debugLineBuffer: IndexBuffer? = null

    @NotSerializedProperty
    private var invalidDebugLines = true

    fun forEachPoint(onlyFaces: Boolean, callback: (x: Float, y: Float, z: Float) -> Unit) {
        val positions = positions ?: return
        val indices = indices
        if (onlyFaces && indices != null) {
            for (i in indices.indices) {
                val ai = indices[i] * 3
                callback(positions[ai], positions[ai + 1], positions[ai + 2])
            }
        } else {
            var i = 0
            val size = positions.size - 2
            while (i < size) {
                callback(positions[i++], positions[i++], positions[i++])
            }
        }
    }

    fun forEachTriangle(callback: (a: Vector3f, b: Vector3f, c: Vector3f) -> Unit) {
        val a = JomlPools.vec3f.create()
        val b = JomlPools.vec3f.create()
        val c = JomlPools.vec3f.create()
        forEachTriangle(a, b, c, callback)
        JomlPools.vec3f.sub(3)
    }

    fun forEachTriangleIndex(callback: TriangleIndexCallback) {
        val positions = positions ?: return
        val indices = indices
        if (indices == null) {
            when (drawMode) {
                DrawMode.TRIANGLES -> {
                    for (i in 0 until positions.size / 9) {
                        val i3 = i * 3
                        callback.run(i3, i3 + 1, i3 + 2)
                    }
                }
                DrawMode.TRIANGLE_STRIP -> {
                    for (i in 2 until positions.size / 3) {
                        val i3 = i * 3
                        callback.run(i3, i3 + 1, i3 + 2)
                    }
                }
                else -> {
                    // no triangles are present
                }
            }
        } else {
            if (indices.size < 3) return
            when (drawMode) {
                DrawMode.TRIANGLES -> {
                    for (i in indices.indices step 3) {
                        callback.run(indices[i], indices[i + 1], indices[i + 2])
                    }
                }
                DrawMode.TRIANGLE_STRIP -> {
                    var a = indices[0]
                    var b = indices[1]
                    for (i in 2 until indices.size) {
                        val c = indices[i]
                        if (a != b && b != c && c != a) {
                            if (i.hasFlag(1)) {
                                callback.run(a, c, b)
                            } else {
                                callback.run(a, b, c)
                            }
                        }
                        a = b
                        b = c
                    }
                }
                else -> {
                    // no triangles are present
                }
            }
        }
    }

    fun countLines(): Int {
        val positions = positions
        val indices = indices
        return max(
            if (indices == null) {
                positions ?: return 0
                val numPoints = positions.size / 3
                when (drawMode) {
                    DrawMode.TRIANGLES -> numPoints
                    DrawMode.TRIANGLE_STRIP -> (numPoints - 2) * 2
                    DrawMode.LINES -> numPoints / 2
                    DrawMode.LINE_STRIP -> numPoints - 1
                    else -> 0
                }
            } else {
                when (drawMode) {
                    DrawMode.TRIANGLES -> indices.size
                    DrawMode.TRIANGLE_STRIP -> (indices.size - 2) * 2
                    DrawMode.LINES -> indices.size / 2
                    DrawMode.LINE_STRIP -> indices.size - 1
                    else -> 0
                }
            }, 0
        )
    }

    fun forEachLineIndex(callback: LineIndexCallback) {
        val positions = positions ?: return
        val indices = indices
        if (indices == null) {
            val numPoints = positions.size / 3
            when (drawMode) {
                DrawMode.TRIANGLES -> {
                    for (i3 in 0 until numPoints - 2 step 3) {
                        callback.run(i3, i3 + 1)
                        callback.run(i3 + 1, i3 + 2)
                        callback.run(i3 + 2, i3)
                    }
                }
                DrawMode.TRIANGLE_STRIP -> {
                    for (c in 2 until numPoints) {
                        callback.run(c - 2, c)
                        callback.run(c - 1, c)
                    }
                }
                DrawMode.LINES -> {
                    for (i in 0 until numPoints step 2) {
                        callback.run(i, i + 1)
                    }
                }
                DrawMode.LINE_STRIP -> {
                    for (i in 1 until numPoints) {
                        callback.run(i - 1, i)
                    }
                }
                else -> {
                    // no lines present
                }
            }
        } else {
            when (drawMode) {
                DrawMode.TRIANGLES -> {
                    for (i in 0 until indices.size - 2 step 3) {
                        val a = indices[i]
                        val b = indices[i + 1]
                        val c = indices[i + 2]
                        callback.run(a, b)
                        callback.run(b, c)
                        callback.run(c, a)
                    }
                }
                DrawMode.TRIANGLE_STRIP -> {
                    var a = indices[0]
                    var b = indices[1]
                    for (i in 2 until indices.size) {
                        val c = indices[i]
                        if (a != b && a != c && b != c) {
                            callback.run(a, c)
                            callback.run(b, c)
                        }
                        a = b
                        b = c
                    }
                }
                DrawMode.LINES -> {
                    for (i in 0 until indices.size - 1 step 2) {
                        callback.run(indices[i], indices[i + 1])
                    }
                }
                DrawMode.LINE_STRIP -> {
                    var a = indices[0]
                    for (i in 1 until indices.size) {
                        val b = indices[i]
                        if (a != b) callback.run(a, b)
                        a = b
                    }
                }
                else -> {}
            }
        }
    }

    fun forEachTriangle(
        a: Vector3f, b: Vector3f, c: Vector3f,
        callback: (a: Vector3f, b: Vector3f, c: Vector3f) -> Unit
    ) {
        val positions = positions ?: return
        val indices = indices
        if (indices != null) {
            if (indices.size < 3) return
            when (drawMode) {
                DrawMode.TRIANGLES -> {
                    for (i in 0 until indices.size - 2 step 3) {
                        a.set(positions, indices[i] * 3)
                        b.set(positions, indices[i + 1] * 3)
                        c.set(positions, indices[i + 2] * 3)
                        callback(a, b, c)
                    }
                }
                DrawMode.TRIANGLE_STRIP -> {
                    var ai = indices[0] * 3
                    var bi = indices[1] * 3
                    for (i in 2 until indices.size) {
                        val ci = indices[i] * 3
                        a.set(positions, ai)
                        // can we remove this?
                        if (i.hasFlag(1)) {
                            b.set(positions, ci)
                            c.set(positions, bi)
                        } else {
                            b.set(positions, bi)
                            c.set(positions, ci)
                        }
                        callback(a, b, c)
                        ai = bi
                        bi = ci
                    }
                }
                else -> {}
            }
        } else {
            when (drawMode) {
                DrawMode.TRIANGLES -> {
                    var i = 0
                    val s = positions.size - 8
                    while (i < s) {
                        a.set(positions, i)
                        b.set(positions, i + 3)
                        c.set(positions, i + 6)
                        callback(a, b, c)
                        i += 9
                    }
                }
                DrawMode.TRIANGLE_STRIP -> {
                    var ai = 0
                    var bi = 3
                    for (i in 2 until positions.size / 3) {
                        val ci = i * 3
                        a.set(positions, ai)
                        // can we remove this?
                        if (i.hasFlag(1)) {
                            b.set(positions, ci)
                            c.set(positions, bi)
                        } else {
                            b.set(positions, bi)
                            c.set(positions, ci)
                        }
                        callback(a, b, c)
                        ai = bi
                        bi = ci
                    }
                }
                else -> {}
            }
        }
    }

    fun forEachTriangle(
        a: Vector3d, b: Vector3d, c: Vector3d,
        callback: (a: Vector3d, b: Vector3d, c: Vector3d) -> Unit
    ) {
        // todo this only supports DrawMode.TRIANGLES
        val positions = positions ?: return
        val indices = indices
        if (indices != null) {
            for (i in 0 until indices.size - 2 step 3) {
                a.set(positions, indices[i] * 3)
                b.set(positions, indices[i + 1] * 3)
                c.set(positions, indices[i + 2] * 3)
                callback(a, b, c)
            }
        } else {
            var i = 0
            val s = positions.size - 8
            while (i < s) {
                a.set(positions, i)
                b.set(positions, i + 3)
                c.set(positions, i + 6)
                callback(a, b, c)
                i += 9
            }
        }
    }

    var hasUVs = false
    override var hasVertexColors = 0
    override var hasBonesInBuffer = false

    override val numPrimitives
        get(): Long {
            val indices = indices
            val positions = positions
            val drawMode = drawMode
            val baseLength = if (indices != null) {
                numPrimitivesByType(indices.size * 3, drawMode)
            } else if (positions != null) {
                numPrimitivesByType(positions.size, drawMode)
            } else 0
            val size = proceduralLength
            return if (size <= 0) baseLength.toLong()
            else if (baseLength > 0) baseLength.toLong() * size
            else numPrimitivesByType(size, drawMode).toLong()
        }

    fun numPrimitivesByType(numPositionValues: Int, drawMode: DrawMode): Int {
        return when (drawMode) {
            DrawMode.POINTS -> numPositionValues / 3
            DrawMode.LINES -> numPositionValues / 6
            DrawMode.LINE_STRIP -> max(0, numPositionValues / 3 - 1)
            DrawMode.TRIANGLE_STRIP -> max(0, numPositionValues / 3 - 2)
            else -> numPositionValues / 9
        }
    }

    var hasHighPrecisionNormals = false

    /** can be set false to use tangents as an additional data channel; notice the RGB[-1,1] limit though */
    var checkTangents = true

    override fun getBounds(): AABBf {
        if (needsBoundsUpdate) {
            if (positions != null) {
                needsBoundsUpdate = false
                calculateAABB()
            } else {
                aabb.all()
            }
        }
        return aabb
    }

    /**
     * upload the data to the gpu, if it has changed
     * */
    override fun ensureBuffer() {
        synchronized(this) {
            if (needsMeshUpdate) updateMesh()
            if (GFX.isGFXThread()) buffer?.ensureBuffer()
        }
    }

    fun ensureNorTanUVs() {

        // not the safest, but well...
        val positions = positions ?: return

        // if normals are null or have length 0, compute them
        if (normals == null)
            normals = FloatArray(positions.size)

        if (tangents == null && uvs != null) // tangents are only computable, if we have uvs
            tangents = FloatArray(positions.size / 3 * 4)

        val normals = normals!!
        val tangents = tangents

        val uvs = uvs
        val hasUVs = uvs != null && uvs.isNotEmpty()
        this.hasUVs = hasUVs

        NormalCalculator.checkNormals(this, positions, normals, indices, drawMode)
        if (hasUVs && checkTangents)
            TangentCalculator.checkTangents(this, positions, normals, tangents, uvs)
    }

    private fun updateMesh() {

        getBounds()

        needsMeshUpdate = false

        // not the safest, but well...
        val positions = positions ?: return
        if (positions.isEmpty()) return

        ensureNorTanUVs()

        val normals = normals!!
        val tangents = tangents

        val uvs = uvs
        val hasUVs = hasUVs

        val color0 = color0
        val color1 = color1
        val color2 = color2
        val color3 = color3
        val boneWeights = boneWeights
        val boneIndices = boneIndices

        val vertexCount = min(positions.size, normals.size) / 3
        val indices = indices

        val hasBones = hasBones
        hasBonesInBuffer = hasBones

        val hasColor0 = color0 != null && color0.isNotEmpty()
        val hasColor1 = color1 != null && color1.isNotEmpty()
        val hasColor2 = color2 != null && color2.isNotEmpty()
        val hasColor3 = color3 != null && color3.isNotEmpty()
        hasVertexColors = hasColor0.toInt() + hasColor1.toInt(2) + hasColor2.toInt(4) + hasColor3.toInt(8)

        val hasHighPrecisionNormals = hasHighPrecisionNormals

        val attributes = arrayListOf(
            Attribute("coords", 3),
        )

        attributes += if (hasHighPrecisionNormals) {
            Attribute("normals", AttributeType.FLOAT, 3)
        } else {
            // todo normals could be oct-encoded
            Attribute("normals", AttributeType.SINT8_NORM, 4)
        }

        if (hasUVs) {
            attributes += Attribute("uvs", 2)
            attributes += Attribute("tangents", AttributeType.SINT8_NORM, 4)
        }

        if (hasColor0) attributes += Attribute("colors0", AttributeType.UINT8_NORM, 4)
        if (hasColor1) attributes += Attribute("colors1", AttributeType.UINT8_NORM, 4)
        if (hasColor2) attributes += Attribute("colors2", AttributeType.UINT8_NORM, 4)
        if (hasColor3) attributes += Attribute("colors3", AttributeType.UINT8_NORM, 4)

        if (hasBones) {
            attributes += Attribute("boneWeights", AttributeType.UINT8_NORM, MAX_WEIGHTS)
            attributes += Attribute("boneIndices", AttributeType.UINT8, MAX_WEIGHTS, true)
        }

        val name = refOrNull?.absolutePath ?: name.ifEmpty { "Mesh" }
        val buffer = replaceBuffer(name, attributes, vertexCount, buffer)
        buffer.drawMode = drawMode
        this.buffer = buffer

        triBuffer = replaceBuffer(buffer, indices, triBuffer)
        triBuffer?.drawMode = drawMode

        for (i in 0 until vertexCount) {

            // upload all data of one vertex

            val i2 = i * 2
            val i3 = i * 3
            val i4 = i * 4

            buffer.put(positions[i3])
            buffer.put(positions[i3 + 1])
            buffer.put(positions[i3 + 2])

            if (hasHighPrecisionNormals) {
                buffer.put(normals[i3])
                buffer.put(normals[i3 + 1])
                buffer.put(normals[i3 + 2])
            } else {
                buffer.putByte(normals[i3])
                buffer.putByte(normals[i3 + 1])
                buffer.putByte(normals[i3 + 2])
                buffer.putByte(0) // alignment
            }

            if (hasUVs) {

                if (uvs != null && i2 + 1 < uvs.size) {
                    buffer.put(uvs[i2])
                    buffer.put(1f - uvs[i2 + 1]) // todo in the future flip the textures instead
                } else buffer.put(0f, 0f)

                if (tangents != null && i4 + 3 < tangents.size) {
                    buffer.putByte(tangents[i4])
                    buffer.putByte(tangents[i4 + 1])
                    buffer.putByte(tangents[i4 + 2])
                    buffer.putByte(tangents[i4 + 3])
                } else {
                    buffer.putByte(normals[i3])
                    buffer.putByte(normals[i3 + 1])
                    buffer.putByte(normals[i3 + 2])
                    buffer.putByte(127) // positive ^^
                }
            }

            fun putColor(colors: IntArray?) {
                if (i < colors!!.size) {
                    val color = colors[i]
                    buffer.putByte(color.r().toByte())
                    buffer.putByte(color.g().toByte())
                    buffer.putByte(color.b().toByte())
                    buffer.putByte(color.a().toByte())
                } else buffer.putInt(-1)
            }

            if (hasColor0) putColor(color0)
            if (hasColor1) putColor(color1)
            if (hasColor2) putColor(color2)
            if (hasColor3) putColor(color3)

            // only works if MAX_WEIGHTS is four
            if (hasBones) {

                if (boneWeights != null && i4 + 3 < boneWeights.size) {
                    val w0 = max(boneWeights[i4], 1e-5f)
                    val w1 = boneWeights[i4 + 1]
                    val w2 = boneWeights[i4 + 2]
                    val w3 = boneWeights[i4 + 3]
                    val normalisation = 255f / (w0 + w1 + w2 + w3)
                    // var w0b = (w0 * normalisation).roundToInt()
                    val w1b = (w1 * normalisation).roundToInt()
                    val w2b = (w2 * normalisation).roundToInt()
                    val w3b = (w3 * normalisation).roundToInt()
                    val w0b = max(255 - (w1b + w2b + w3b), 0)
                    buffer.putByte(w0b.toByte())
                    buffer.putByte(w1b.toByte())
                    buffer.putByte(w2b.toByte())
                    buffer.putByte(w3b.toByte())
                } else {
                    buffer.putByte(-1)
                    buffer.putByte(0)
                    buffer.putByte(0)
                    buffer.putByte(0)
                }

                if (boneIndices != null && i4 + 3 < boneIndices.size) {
                    buffer.putByte(boneIndices[i4])
                    buffer.putByte(boneIndices[i4 + 1])
                    buffer.putByte(boneIndices[i4 + 2])
                    buffer.putByte(boneIndices[i4 + 3])
                } else {
                    buffer.putInt(0)
                }
            }
        }

        updateHelperMeshes()

        // LOGGER.info("Flags($name): size: ${buffer.vertexCount}, colors? $hasColors, uvs? $hasUVs, bones? $hasBones")

        // find regular lines
        lineIndices = lineIndices ?: FindLines.findLines(this, indices, positions)
        lineBuffer = replaceBuffer(buffer, lineIndices, lineBuffer)
        lineBuffer?.drawMode = DrawMode.LINES

        invalidDebugLines = true
    }

    fun updateHelperMeshes() {
        val materialIds = materialIds
        val first = materialIds?.firstOrNull() ?: 0
        val hasMultipleMaterials = materialIds != null && materialIds.any { it != first }
        if (hasMultipleMaterials) {
            createHelperMeshes(materialIds!!)
        } else {
            destroyHelperMeshes()
            numMaterials = 1
        }
    }

    fun ensureDebugLines() {
        val buffer = buffer
        if (invalidDebugLines && buffer != null) {
            invalidDebugLines = false
            debugLineIndices = FindLines.getAllLines(this, indices)
            debugLineBuffer = replaceBuffer(buffer, debugLineIndices, debugLineBuffer)
            debugLineBuffer?.drawMode = DrawMode.LINES
        }
    }

    fun createHelperMeshes(materialIds: IntArray, init: Boolean = true) {
        // todo use the same geometry data buffers: allow different index buffers per buffer
        // lines, per-material, all-together
        // creating separate buffers on the gpu,
        // split indices / data, would be of advantage here
        val length = materialIds.maxOrNull()!! + 1
        if (length == 1) return
        if (drawMode != DrawMode.TRIANGLES) throw IllegalStateException("Multi-material meshes only supported on triangle meshes; got $drawMode")
        if (length > 1000) throw IllegalStateException("Material Id must be less than 1000!")
        val helperMeshes = createArrayList<HelperMesh?>(length, null)
        val indices = indices
        for (materialId in 0 until length) {
            val numTriangles = materialIds.count { it == materialId }
            if (numTriangles > 0) {
                val helperIndices = IntArray(numTriangles * 3)
                var j = 0
                var i3 = 0
                if (indices == null) {
                    for (i in materialIds.indices) {
                        val id = materialIds[i]
                        if (id == materialId) {
                            helperIndices[j++] = i3++
                            helperIndices[j++] = i3++
                            helperIndices[j++] = i3++
                        } else i3 += 3
                    }
                } else {
                    if (indices.size != materialIds.size * 3)
                        throw IllegalStateException("Material IDs must be exactly 3x smaller than indices")
                    for (i in materialIds.indices) {
                        val id = materialIds[i]
                        if (id == materialId) {
                            helperIndices[j++] = indices[i3++]
                            helperIndices[j++] = indices[i3++]
                            helperIndices[j++] = indices[i3++]
                        } else i3 += 3
                    }
                }
                if (j != helperIndices.size) throw IllegalStateException("Ids must have changed while processing")
                val helper = HelperMesh(helperIndices)
                if (init) helper.init(this)
                helperMeshes[materialId] = helper
            }// else mesh not required
        }
        this.helperMeshes = helperMeshes
        numMaterials = length
    }

    private fun destroyHelperMeshes() {
        val hm = helperMeshes
        if (hm != null) for (it in hm) it?.destroy()
        helperMeshes = null
    }

    override fun destroy() {
        super.destroy()
        // todo only if we were not cloned...
        destroyHelperMeshes()
        clearGPUData()
        clearCPUData()
    }

    fun clearGPUData() {
        buffer?.destroy()
        triBuffer?.destroy()
        lineBuffer?.destroy()
        debugLineBuffer?.destroy()
        buffer = null
        triBuffer = null
        debugLineBuffer = null
    }

    fun clearCPUData() {
        positions = null
        normals = null
        uvs = null
        tangents = null
        color0 = null
        color1 = null
        color2 = null
        color3 = null
        color4 = null
        color5 = null
        color6 = null
        color7 = null
        indices = null
        boneWeights = null
        boneIndices = null
    }

    fun draw(shader: Shader, materialIndex: Int) {
        draw(shader, materialIndex, drawDebugLines)
    }

    override fun draw(shader: Shader, materialIndex: Int, drawLines: Boolean) {
        val proceduralLength = proceduralLength
        if (proceduralLength <= 0) {
            ensureBuffer()
            // respect the material index: only draw what belongs to the material
            val helperMeshes = helperMeshes
            when {
                helperMeshes != null && materialIndex in helperMeshes.indices -> {
                    val helperMesh = helperMeshes[materialIndex] ?: return
                    if (drawLines) {
                        helperMesh.ensureDebugLines(this)
                        helperMesh.debugLineBuffer?.draw(shader)
                    } else {
                        helperMesh.triBuffer?.draw(shader)
                        helperMesh.lineBuffer?.draw(shader)
                    }
                }
                materialIndex == 0 -> {
                    if (drawLines) {
                        ensureDebugLines()
                        debugLineBuffer?.draw(shader)
                    } else {
                        (triBuffer ?: buffer)?.draw(shader)
                        lineBuffer?.draw(shader)
                    }
                }
            }
        } else if ((positions?.size ?: 0) == 0) {
            StaticBuffer.drawArraysNull(shader, drawMode, proceduralLength)
        } else {
            if (drawLines) {
                ensureDebugLines()
                debugLineBuffer?.drawInstanced(shader, proceduralLength)
            } else {
                (triBuffer ?: buffer)?.drawInstanced(shader, proceduralLength)
                lineBuffer?.drawInstanced(shader, proceduralLength)
            }
        }
    }

    override fun drawInstanced(shader: Shader, materialIndex: Int, instanceData: Buffer, drawLines: Boolean) {
        if (proceduralLength <= 0) {
            GFX.check()
            ensureBuffer()
            // respect the material index: only draw what belongs to the material
            val helperMeshes = helperMeshes
            if (helperMeshes != null) {
                val helperMesh = helperMeshes.getOrNull(materialIndex)
                if (helperMesh != null) {
                    if (drawDebugLines) {
                        helperMesh.ensureDebugLines(this)
                        helperMesh.debugLineBuffer?.drawInstanced(shader, instanceData)
                    } else {
                        helperMesh.triBuffer?.drawInstanced(shader, instanceData)
                        helperMesh.lineBuffer?.drawInstanced(shader, instanceData)
                    }
                }
            } else if (materialIndex == 0) {
                if (drawDebugLines) {
                    ensureDebugLines()
                    debugLineBuffer?.drawInstanced(shader, instanceData)
                } else {
                    (triBuffer ?: buffer)?.drawInstanced(shader, instanceData)
                    lineBuffer?.drawInstanced(shader, instanceData)
                }
            }
            GFX.check()
        } else LOGGER.warn("Instanced rendering of procedural meshes is not supported!")
    }

    /**
     * calculates the bounds of the mesh
     * not fast, but the gpu will take just as long -> doesn't matter
     *
     * the goal is to be accurate
     * */
    fun getBounds(transform: Matrix4f, onlyFaces: Boolean): AABBf {
        val vf = Vector3f()
        val aabb = AABBf()
        getBounds()
        forEachPoint(onlyFaces) { x, y, z ->
            aabb.union(transform.transformProject(vf.set(x, y, z)))
        }
        return aabb
    }

    fun makeFlatShaded(calculateNormals: Boolean = true) {
        val indices = indices
        val positions = positions ?: return
        val colors = color0
        if (indices == null) {
            if (calculateNormals) calculateNormals(false)
        } else {
            val newPositions = FloatArray(indices.size * 3)
            val newColors = if (colors != null) IntArray(indices.size) else null
            for (i in indices.indices) {
                val i3 = i * 3
                val j = indices[i]
                val j3 = j * 3
                newPositions[i3] = positions[j3]
                newPositions[i3 + 1] = positions[j3 + 1]
                newPositions[i3 + 2] = positions[j3 + 2]
                if (colors != null) newColors!![i] = colors[j]
            }
            this.positions = newPositions
            this.normals = normals.resize(newPositions.size)
            this.color0 = newColors
            this.indices = null
            if (calculateNormals) calculateNormals(false)
        }
    }

    fun makeLineMesh(keepOnlyUniqueLines: Boolean) {
        val indices = if (keepOnlyUniqueLines) {
            val lines = HashSet<IntPair>()
            forEachLineIndex { a, b ->
                if (a != b) {
                    lines += IntPair(min(a, b), max(a, b))
                }
            }
            var ctr = 0
            val indices = indices.resize(lines.size * 2)
            for (line in lines) {
                indices[ctr++] = line.first
                indices[ctr++] = line.second
            }
            indices
        } else {
            var ctr = 0
            val indices = indices.resize(countLines() * 2)
            forEachLineIndex { a, b ->
                indices[ctr++] = a
                indices[ctr++] = b
            }
            indices
        }
        this.indices = indices
        drawMode = DrawMode.LINES
        invalidateGeometry()
    }

    override fun fill(
        pipeline: Pipeline,
        entity: Entity,
        clickId: Int
    ): Int {
        pipeline.addMesh(this, Pipeline.sampleMeshComponent, entity)
        return clickId
    }

    @DebugAction
    fun centerXZonY() {
        val bounds = getBounds()
        val dx = -bounds.centerX
        val dy = -bounds.minY
        val dz = -bounds.centerZ
        move(dx, dy, dz)
    }

    @DebugAction
    fun centerXYZ() {
        val bounds = getBounds()
        val dx = -bounds.centerX
        val dy = -bounds.centerY
        val dz = -bounds.centerZ
        move(dx, dy, dz)
    }

    @DebugAction
    fun move(dx: Float, dy: Float, dz: Float) {
        if (prefab?.isWritable == false) {
            LOGGER.warn("Mesh is immutable")
        } else {
            val positions = positions ?: return
            for (i in 0 until positions.size - 2 step 3) {
                positions[i] += dx
                positions[i + 1] += dy
                positions[i + 2] += dz
            }
            invalidateGeometry()
        }
    }

    @DebugAction
    fun scaleUp100x() {
        scale(Vector3f(100f))
    }

    @DebugAction
    fun scaleDown100x() {
        scale(Vector3f(0.01f))
    }

    fun scale(factor: Vector3f) {
        val positions = positions ?: return
        for (i in positions.indices step 3) {
            positions[i] *= factor.x
            positions[i + 1] *= factor.y
            positions[i + 2] *= factor.z
        }
        invalidateGeometry()
    }

    @DebugAction
    fun removeVertexColors() {
        if (prefab?.isWritable == false) {
            LOGGER.warn("Mesh is immutable")
        } else {
            color0 = null
            prefab?.set("color0", null)
            invalidateGeometry()
        }
    }

    companion object {

        val drawDebugLines: Boolean
            get() {
                val mode = RenderView.currentInstance?.renderMode
                return mode == RenderMode.LINES || mode == RenderMode.LINES_MSAA
            }

        private val defaultMaterials = emptyList<FileReference>()
        private val LOGGER = LogManager.getLogger(Mesh::class)

        // custom attributes for shaders? idk...
        // will always be 4, so bone indices can be aligned
        const val MAX_WEIGHTS = 4

        private fun replaceBuffer(
            name: String,
            attributes: List<Attribute>,
            vertexCount: Int,
            oldValue: StaticBuffer?,
        ): StaticBuffer {
            if (oldValue != null) {
                // offsets are compared, so they need to be consistent
                computeOffsets(attributes)
                computeOffsets(oldValue.attributes)
                if (oldValue.attributes == attributes && oldValue.vertexCount == vertexCount) {
                    oldValue.clear()
                    return oldValue
                } else {
                    oldValue.destroy()
                }
            }
            return StaticBuffer(name, attributes, vertexCount)
        }

        private fun replaceBuffer(base: Buffer, indices: IntArray?, oldValue: IndexBuffer?): IndexBuffer? {
            return if (indices != null) {
                if (oldValue != null) {
                    if (base === oldValue.base) {
                        oldValue.indices = indices
                        return oldValue
                    } else oldValue.destroy()
                }
                IndexBuffer(base.name, base, indices)
            } else {
                oldValue?.destroy()
                null
            }
        }
    }
}