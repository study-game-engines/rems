package me.anno.tests.engine.material

import me.anno.ecs.components.mesh.ImagePlane
import me.anno.ecs.components.mesh.TypeValueTex
import me.anno.engine.ui.render.ECSMeshShader
import me.anno.engine.ui.render.SceneView.Companion.testSceneWithUI
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.TextureLib.blackTexture
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.InvalidRef

/**
 * inspired by Jam2go, https://www.youtube.com/watch?v=LGkblrCmzlE
 * */
object ScreenShader : ECSMeshShader("Screen") {
    override fun createFragmentStages(key: ShaderKey): List<ShaderStage> {
        return super.createFragmentStages(key) + ShaderStage(
            "Screen", listOf(
                Variable(GLSLType.S2D, "screenTexture"),
                Variable(GLSLType.V2F, "screenResolution"),
                Variable(GLSLType.V3F, "finalEmissive", VariableMode.INOUT),
                Variable(GLSLType.V2F, "uv"),
                Variable(GLSLType.S2D, "emissiveMap")
            ), colorToSRGB + // ensure sRGB space before multiplying, because our texture is sRGB
                    "ivec2 texSize = textureSize(emissiveMap,0);\n" +
                    "if(max(texSize.x,texSize.y) > 1) { finalEmissive *= texture(screenTexture, uv * vec2(texSize)).rgb; }"
        )
    }
}

fun main() {
    // create image plane
    val mask = getReference("res://rgbMask.png")
    val image = getReference("res://dig8.png")
    val plane = ImagePlane(image)
    plane.material.apply {
        shader = ScreenShader
        diffuseBase.set(0f, 0f, 0f, 1f)
        diffuseMap = InvalidRef
        emissiveMap = image
        emissiveBase.set(7f)
        linearFiltering = false // we render pixels 😄
        shaderOverrides["screenTexture"] =
            TypeValueTex(GLSLType.S2D, mask, GPUFiltering.LINEAR, Clamping.REPEAT, blackTexture)
    }
    testSceneWithUI("Screen Material", plane)
}