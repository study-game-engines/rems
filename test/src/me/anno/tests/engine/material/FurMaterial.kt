package me.anno.tests.engine.material

import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshCache
import me.anno.ecs.components.mesh.material.FurMeshComponent
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.utils.OS.downloads

/**
 * implement shell texturing like in https://www.youtube.com/watch?v=9dr-tRQzij4 by Acerola
 * */
fun main() {
    val mesh = MeshCache[downloads.getChild("3d/bunny.obj")]!!.clone() as Mesh
    mesh.calculateNormals(true) // clone and recalculating normals, because the bunny file, I have, has flat normals
    val scene = Entity()
    scene.add(FurMeshComponent(mesh))
    testSceneWithUI("Shell Textures", scene)
}

// todo implement screen-space global illumination like Blender Eevee Next