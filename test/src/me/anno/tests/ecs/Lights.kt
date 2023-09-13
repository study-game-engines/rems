package me.anno.tests.ecs

import me.anno.ecs.components.mesh.Material
import me.anno.ecs.prefab.Prefab
import me.anno.ecs.prefab.change.Path
import me.anno.engine.ECSRegistry
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.io.files.FileReference
import me.anno.maths.Maths
import me.anno.mesh.Shapes.flatCube
import me.anno.ui.editor.color.spaces.HSLuv
import me.anno.utils.Clock
import me.anno.utils.OS
import me.anno.utils.OS.documents
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * recreate colorful test scene with 100k lights from ages ago :3
 * */
fun main() {

    ECSRegistry.init()

    val clock = Clock()

    val prefab = Prefab()
    fun set(path: Path, key: String, value: Any) {
        prefab[path, key] = value
    }

    fun addE(path: Path, name: String): Path {
        return prefab.add(path, 'e', "Entity", name)
    }

    fun addE(path: Path, name: String, src: FileReference): Path {
        return prefab.add(path, 'e', "Entity", name, src)
    }

    fun addC(path: Path, clazz: String, name: String = clazz): Path {
        if (clazz.isEmpty()) throw IllegalArgumentException()
        return prefab.add(path, 'c', clazz, name)
    }

    prefab.ensureMutableLists()

    set(Path.ROOT_PATH, "name", "Root")
    set(Path.ROOT_PATH, "description", "Contains the major components")
    set(Path.ROOT_PATH, "isCollapsed", false)

    val names = listOf("Globally Shared", "Player Prefab", "Locally Shared", "Local Players", "Remote Players")
    val descriptions = listOf(
        "The world, which is shared",
        "What a player in the global world looks like",
        "If there is UI to be shared for local multiplayer, define it here",
        "Populated at runtime with the players on this PC; can be trusted",
        "Populated at runtime with players from different PCs, states, continents; may not be trusted"
    )

    val root = Path.ROOT_PATH
    for (i in names.indices) {
        val e = addE(root, names[i])
        set(e, "description", descriptions[i])
        set(e, "isCollapsed", false)
    }

    // root has bullet physics, because the players need physics as well
    // addC(root, "BulletPhysics")

    clock.stop("main things")

    // just add stuff for debugging :)
    //////////////////
    // sample mesh //
    ////////////////
    val world = root.added(names[0], 0, 'e')
    /*val truck = */addE(world, "VOX/Truck", OS.downloads.getChild("MagicaVoxel/vox/truck.vox"))
    // val truckBody0 = truck.added("", 0, 'e')
    // addC(truckBody0, "MeshCollider")
    // set(truck, "isCollapsed", false)
    //val truckRigidbody = addC(Unit, truckBody0, "Rigidbody")
    //set(truckRigidbody, "mass", 100.0)

    clock.stop("truck")

    /////////////////////////
    // sample point light //
    ///////////////////////

    val lights = addE(world, "Lights")
    addC(lights, "Skybox")
    val ambient = addC(lights, "AmbientLight")
    set(ambient, "color", Vector3f(0.25f))

    val sun = addE(lights, "Sun")
    set(sun, "scale", Vector3d(50.0))
    set(sun, "position", Vector3d(0.0, -10.0, 0.0))
    set(sun, "rotation", Quaterniond().rotateY(0.8).rotateX(-0.8))
    val dl = addC(sun, "DirectionalLight")
    set(dl, "shadowMapCascades", 1)
    set(dl, "color", Vector3f(3f))

    clock.stop("lights")

    // todo why appear the outest rings brightest???

    val ringOfLights = addE(lights, "Ring Of Lights")
    val superRings = 200
    val elementSize = 10.0
    val lightLevel = 20f
    val numColors = 3
    val colorRepetitions = 3
    val colors = Array(numColors) {
        val angle = it / numColors.toFloat()
        HSLuv.toRGB(Vector3f(angle, 1f, 0.7f)).mul(lightLevel)
    }
    val scale = Vector3d(elementSize)
    for (j in 0 until superRings) {
        val superRing = addE(ringOfLights, "Ring[$j]")
        val radius = 50.0 * (1.0 + j * 0.1)
        val ringLightCount = (radius * 0.5).toInt()
        for (i in 0 until ringLightCount) {
            val angle = Maths.TAU * i.toDouble() / ringLightCount
            val light = addE(superRing, "L$i")
            val position = Vector3d(radius * cos(angle), elementSize * 0.5, radius * sin(angle))
            set(light, "position", position)
            set(light, "scale", scale)
            val c = addC(light, "PointLight", "PL$i")
            set(c, "color", colors[(numColors * colorRepetitions * i / ringLightCount) % numColors])
        }
    }

    clock.stop("ring of lights")

    // add a floor for testing
    val cubePath = flatCube.front.ref
    val floor = addE(world, "Floor")
    set(floor, "position", Vector3d(0.0, -50.0, 0.0))
    set(floor, "scale", Vector3d(2000.0, 50.0, 2000.0))
    // set(floor, "scale", Vector3d(5.0))
    val floorBody = addC(floor, "Rigidbody")
    set(floorBody, "mass", 0.0) // static
    val floorCollider = addC(floor, "BoxCollider")
    set(floorCollider, "halfExtends", Vector3d(1.0))
    val floorMesh1E = addE(floor, "Metallic")
    set(floorMesh1E, "position", Vector3d(0.5, 0.0, 0.0))
    set(floorMesh1E, "scale", Vector3d(0.5, 1.0, 1.0))
    val floorMesh2E = addE(floor, "Rough")
    set(floorMesh2E, "position", Vector3d(-0.5, 0.0, 0.0))
    set(floorMesh2E, "scale", Vector3d(0.5, 1.0, 1.0))
    val floorMesh1 = addC(floorMesh1E, "MeshComponent")
    set(floorMesh1, "meshFile", cubePath)
    // todo why is the ssr-reflectivity so low?
    set(floorMesh1, "materials", listOf(Material().apply {
        metallicMinMax.set(1f)
        roughnessMinMax.set(0.2f)
    }.ref))
    val floorMesh2 = addC(floorMesh2E, "MeshComponent")
    set(floorMesh2, "meshFile", cubePath)

    clock.stop("floor")

    // add spheres for testing
    /*val sphereMesh = OS.documents.getChild("sphere.obj")
    for (i in 0 until 100) {
        val sphere = addE(Unit, physics, "Sphere[$i]", sphereMesh)
        set(sphere, "position", Vector3d(0.0, (i + 2) * 2.1, 0.0))
        addC(Unit, sphere, "Rigidbody")
        addC(Unit, sphere, "SphereCollider")
    }*/

    // add a cube of cubes for frustum testing
    /*val frustum = addE(Unit, world, "Frustum Testing")
    for (x in -5..5) {
        // for testing bounding boxes more
        val xGroup = addE(Unit, frustum, "Group-$x")
        for (y in -5..5) {
            for (z in -5..5) {
                // meshes
                val cube = addE(Unit, xGroup, "Cube[$x,$y,$z]", cubePathNormals)
                set(cube, "position", Vector3d(x * 5.0, y * 5.0 + 30.0, z * 5.0))
                // a little random rotation
                // val q = Quaterniond(Math.random(), Math.random(), Math.random(), Math.random()).normalize()
                // set(cube, "rotation", q)
                // physics test
                addC(Unit, cube, "BoxCollider")
                addC(Unit, cube, "Rigidbody")
                val meshComp = Path(cube, "MeshComponent", 0, 'c')
                set(meshComp, "isInstanced", true)
            }
        }
    } // */

    // normal testing
    /*val normalTesting = addE(Unit, world, "Normal Testing")
    val l = 100
    for (i in 0 until l) {
        for (j in 0 until 2) {
            val cube = addE(Unit, normalTesting, "Cube[$i]", cubePathNormals)
            val angle = (i + (j.and(1) * 0.5)) * 6.2830 / l
            set(cube, "position", Vector3d(cos(angle) * l / 2, 1.0 + 2 * j, sin(angle) * l / 2))
            set(cube, "rotation", Quaterniond().rotateY(-angle).rotateX(j * 6.2830 / 8))
        }
    }*/

    // row of planets
    /*val spherePath = OS.documents.getChild("sphere.obj")
    val planets = addE(Unit, world, "Planets")
    for (i in -50..50) {
        val size = 10.0.pow(i.toDouble())
        val sphere = addE(Unit, planets, "Sphere 1e$i", spherePath)
        set(sphere, "position", Vector3d(0.0, 0.0, 3.0 * size))
        set(sphere, "scale", Vector3d(size))
    }*/

    testSceneWithUI("Lights", prefab)
}