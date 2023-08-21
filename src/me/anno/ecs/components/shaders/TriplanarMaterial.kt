package me.anno.ecs.components.shaders

import me.anno.ecs.annotations.Range
import me.anno.ecs.components.mesh.Material
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.ui.render.RenderState
import me.anno.gpu.shader.Shader
import org.joml.Vector4f
import kotlin.math.min

class TriplanarMaterial : Material() {

    @Range(0.0, 1.0)
    var sharpness: Float = 0.7f

    @Range(0.0, 1.0)
    var blendPreferY: Float = 0.675f

    var primaryTiling: Vector4f = Vector4f(1f, 1f, 0f, 0f)

    init {
        shader = TriplanarShader
    }

    override fun bind(shader: Shader) {
        super.bind(shader)
        shader.v1f("sharpness", if (blendPreferY > 0f) sharpness else min(sharpness, 0.999f))
        shader.v1f("blendPreferY", blendPreferY)
        shader.v4f("primaryTiling", primaryTiling)
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as TriplanarMaterial
        dst.sharpness = sharpness
        dst.blendPreferY = blendPreferY
        dst.primaryTiling.set(primaryTiling)
    }

    override val className: String get() = "TriplanarMaterial"

}