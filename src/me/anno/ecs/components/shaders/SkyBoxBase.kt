package me.anno.ecs.components.shaders

import me.anno.Engine
import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Material
import me.anno.ecs.components.mesh.MeshComponentBase
import me.anno.ecs.components.mesh.TypeValue
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.raycast.RayHit
import me.anno.engine.ui.render.ECSMeshShader
import me.anno.gpu.GFXState
import me.anno.gpu.pipeline.Pipeline
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.ShaderLib.quatRot
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.io.serialization.NotSerializedProperty
import me.anno.io.serialization.SerializedProperty
import me.anno.maths.Maths.hasFlag
import me.anno.mesh.Shapes
import org.joml.*

open class SkyBoxBase : MeshComponentBase() {

    // todo make this a light, such that all things can be lighted from it

    // todo override raytracing for clicking: if ray goes far enough, let it click us

    init {
        castShadows = false
        receiveShadows = false
    }

    @SerializedProperty
    var shader: SkyShaderBase?
        get() = material.shader as? SkyShaderBase
        set(value) {
            material.shader = value
        }

    @NotSerializedProperty
    val material: Material = Material()

    @SerializedProperty
    var skyColor: Vector3f = Vector3f(0.2f, 0.4f, 0.6f)
        set(value) {
            field.set(value)
        }

    @SerializedProperty
    var worldRotation = Quaternionf()
        set(value) {
            field.set(value)
        }

    init {
        material.shader = defaultShaderBase
        material.shaderOverrides["skyColor"] = TypeValue(GLSLType.V3F, skyColor)
        material.shaderOverrides["worldRot"] = TypeValue(GLSLType.V4F, worldRotation)
        material.shaderOverrides["reversedDepth"] = TypeValue(GLSLType.V1B, {
            GFXState.depthMode.currentValue.reversedDepth
        })
        materials = listOf(material.ref)
    }

    override fun hasRaycastType(typeMask: Int) = false
    override fun raycast(
        entity: Entity,
        start: Vector3d,
        direction: Vector3d,
        end: Vector3d,
        radiusAtOrigin: Double,
        radiusPerUnit: Double,
        typeMask: Int,
        includeDisabled: Boolean,
        result: RayHit
    ) = false

    override fun fill(
        pipeline: Pipeline,
        entity: Entity,
        clickId: Int
    ): Int {
        lastDrawn = Engine.gameTime
        pipeline.skyBox = this
        this.clickId = clickId
        return clickId + 1
    }

    override fun getMesh() = mesh

    init {
        globalAABB.all()
        localAABB.all()
    }

    override fun fillSpace(globalTransform: Matrix4x3d, aabb: AABBd): Boolean {
        aabb.all() // skybox is visible everywhere
        return true
    }

    override fun copyInto(dst: PrefabSaveable) {
        super.copyInto(dst)
        dst as SkyBoxBase
        dst.shader = shader
        dst.skyColor.set(skyColor)
        dst.worldRotation.set(worldRotation)
    }

    override val className: String get() = "SkyBox"

    companion object {

        val mesh = Shapes.smoothCube.back

        open class SkyShaderBase(name: String) : ECSMeshShader(name) {

            override fun createVertexStages(flags: Int): List<ShaderStage> {
                val defines = if (flags.hasFlag(NEEDS_COLORS)) "#define COLORS\n" else ""
                return listOf(
                    ShaderStage(
                        "vertex",
                        createVertexVariables(flags) +
                                listOf(Variable(GLSLType.V1B, "reversedDepth")),
                        defines +
                                "localPosition = coords;\n" +
                                "finalPosition = localPosition;\n" +
                                "#ifdef COLORS\n" +
                                "   normal = -coords;\n" +
                                "#endif\n" +
                                "gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
                                "gl_Position.z = (reversedDepth ? 1e-36 : 0.9999995) * gl_Position.w;\n" +
                                ShaderLib.positionPostProcessing
                    )
                )
            }

            override fun createFragmentStages(flags: Int): List<ShaderStage> {
                // todo the red clouds in the night sky are a bit awkward
                val stage = ShaderStage(
                    "sky", listOf(
                        Variable(GLSLType.V3F, "normal", VariableMode.IN),
                        Variable(GLSLType.V3F, "finalNormal", VariableMode.OUT),
                        Variable(GLSLType.V3F, "finalPosition", VariableMode.OUT),
                        Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
                        Variable(GLSLType.V3F, "finalEmissive", VariableMode.OUT),
                        Variable(GLSLType.V4F, "worldRot"),
                        Variable(GLSLType.V3F, "skyColor")
                    ), "" +
                            // sky no longer properly defined for y > 0
                            "finalNormal = normalize(-normal);\n" +
                            "finalColor = vec3(0.0);\n" +
                            "finalEmissive = getSkyColor(quatRot(finalNormal, worldRot));\n" +
                            "finalNormal = -finalNormal;\n" +
                            "finalPosition = finalNormal * 1e20;\n"
                )
                stage.add(quatRot)
                stage.add(getSkyColor())
                return listOf(stage)
            }

            open fun getSkyColor(): String = "vec3 getSkyColor(vec3 pos){ return skyColor; }"

        }

        val defaultShaderBase = SkyShaderBase("skyBase")
            .apply {
                ignoreNameWarnings(
                    "diffuseBase", "normalStrength", "emissiveBase",
                    "roughnessMinMax", "metallicMinMax", "occlusionStrength", "finalTranslucency", "finalClearCoat",
                    "tint", "hasAnimation", "localTransform", "invLocalTransform", "worldScale", "tiling",
                    "forceFieldColorCount", "forceFieldUVCount",
                )
            }

        val defaultSky = SkyBoxBase()

    }

}