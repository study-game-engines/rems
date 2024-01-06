package me.anno.gpu.pipeline

import me.anno.ecs.components.light.LightType
import me.anno.ecs.components.mesh.MeshInstanceData
import me.anno.engine.pbr.PBRLibraryGLTF.specularBRDFv2NoColor
import me.anno.engine.pbr.PBRLibraryGLTF.specularBRDFv2NoColorEnd
import me.anno.engine.pbr.PBRLibraryGLTF.specularBRDFv2NoColorStart
import me.anno.engine.ui.render.ECSMeshShader.Companion.colorToLinear
import me.anno.engine.ui.render.ECSMeshShader.Companion.colorToSRGB
import me.anno.engine.ui.render.RendererLib.sampleSkyboxForAmbient
import me.anno.engine.ui.render.Renderers
import me.anno.engine.ui.render.Renderers.tonemapGLSL
import me.anno.gpu.GFX
import me.anno.gpu.GFXState
import me.anno.gpu.buffer.Attribute
import me.anno.gpu.buffer.SimpleBuffer.Companion.flat01
import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.deferred.DeferredLayerType
import me.anno.gpu.deferred.DeferredSettings
import me.anno.gpu.framebuffer.IFramebuffer
import me.anno.gpu.pipeline.PipelineStage.Companion.instancedBatchSize
import me.anno.gpu.shader.DepthTransforms.bindDepthToPosition
import me.anno.gpu.shader.DepthTransforms.depthToPosition
import me.anno.gpu.shader.DepthTransforms.depthVars
import me.anno.gpu.shader.DepthTransforms.rawToDepth
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.GLSLType.Companion.floats
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib.coordsList
import me.anno.gpu.shader.ShaderLib.loadMat4x3
import me.anno.gpu.shader.ShaderLib.octNormalPacking
import me.anno.gpu.shader.ShaderLib.quatRot
import me.anno.gpu.shader.builder.ShaderBuilder
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.texture.*
import me.anno.utils.Color.black4
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.types.Booleans.toInt
import org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW

object LightShaders {

    private val lightInstancedAttributes = listOf(
        // transform
        Attribute("instanceTrans0", 4),
        Attribute("instanceTrans1", 4),
        Attribute("instanceTrans2", 4),
        // inverse transform for light mapping
        Attribute("invInsTrans0", 4),
        Attribute("invInsTrans1", 4),
        Attribute("invInsTrans2", 4),
        // light properties like type, color, cone angle
        Attribute("lightData0", 4),
        Attribute("lightData1", 1),
        Attribute("shadowData", 4)
        // instanced rendering does not support shadows -> no shadow data / as a uniform
    )

    // test: can we get better performance, when we eliminate dependencies?
    // pro: fewer dependencies
    // con: we use more vram
    // con: we remove cache locality
    /*private val lightInstanceBuffers = Array(512) {
        StaticBuffer(lightInstancedAttributes, instancedBatchSize, GL_DYNAMIC_DRAW)
    }
    private var libIndex = 0
    // performance: the same
    */

    val lightInstanceBuffer = StaticBuffer(
        "lights",
        lightInstancedAttributes,
        instancedBatchSize,
        GL_DYNAMIC_DRAW
    )

    val useMSAA get() = GFXState.currentBuffer.samples > 1

    fun combineLighting(
        deferred: DeferredSettings, applyToneMapping: Boolean, scene: IFramebuffer,
        light: IFramebuffer, ssao: ITexture2D, skybox: CubemapTexture
    ) {
        val shader = getCombineLightShader(deferred)
        shader.use()
        scene.bindTrulyNearestMS(shader.getTextureIndex("defLayer0"))
        val metallic = deferred.findLayer(DeferredLayerType.METALLIC)
        (deferred.findTextureMS(scene, metallic) as? Texture2D ?: TextureLib.blackTexture).bindTrulyNearest(3)
        shader.v4f("metallicMask", DeferredSettings.singleToVector[metallic?.mapping] ?: black4)
        skybox.bind(shader.getTextureIndex("skybox"), Filtering.LINEAR)
        ssao.bindTrulyNearest(shader, "occlusionTex")
        light.bindTrulyNearestMS(shader.getTextureIndex("lightTex"))
        combineLighting1(shader, applyToneMapping)
    }

    fun combineLighting1(shader: Shader, applyToneMapping: Boolean) {
        shader.v1b("applyToneMapping", applyToneMapping)
        bindDepthToPosition(shader)
        flat01.draw(shader)
    }

    fun bindNullDepthTextures(shader: Shader) {
        val depthTexture = if (GFX.supportsDepthTextures) TextureLib.depthTexture else TextureLib.blackTexture
        for (i in 0 until 8) {
            val tx = shader.getTextureIndex("shadowMapPlanar$i")
            if (tx < 0) break
            depthTexture.bindTrulyNearest(tx)
        }
        val depthCube = if (GFX.supportsDepthTextures) TextureLib.depthCube else TextureLib.blackCube
        for (i in 0 until 8) {
            val tx = shader.getTextureIndex("shadowMapCubic$i")
            if (tx < 0) break
            depthCube.bindTrulyNearest(tx)
        }
    }

    val combineVStage = ShaderStage(
        "combineLight-v",
        coordsList + Variable(GLSLType.V2F, "uv", VariableMode.OUT),
        "gl_Position = vec4(coords*2.0-1.0,0.5,1.0);\nuv = coords;\n"
    )

    val combineFStage = ShaderStage(
        "combineLight-f", listOf(
            Variable(GLSLType.V3F, "finalColor", VariableMode.INMOD),
            Variable(GLSLType.V3F, "finalEmissive", VariableMode.INMOD),
            Variable(GLSLType.SCube, "skybox"),
            Variable(GLSLType.V3F, "finalNormal"),
            Variable(GLSLType.V1F, "finalMetallic"),
            Variable(GLSLType.V1F, "finalRoughness"),
            Variable(GLSLType.V1F, "finalOcclusion"),
            Variable(GLSLType.V1B, "applyToneMapping"),
            Variable(GLSLType.V3F, "finalLight"),
            Variable(GLSLType.V1F, "ambientOcclusion"),
            Variable(GLSLType.V4F, "color", VariableMode.OUT)
        ), "" +
                colorToLinear +
                "   vec3 light = finalLight + sampleSkyboxForAmbient(finalNormal, finalMetallic, finalRoughness);\n" +
                "   float invOcclusion = (1.0 - finalOcclusion) * (1.0 - ambientOcclusion);\n" +
                "   finalColor = finalColor * light * pow(invOcclusion, 2.0) + finalEmissive * mix(1.0, invOcclusion, finalMetallic);\n" +
                colorToSRGB +
                "   if(applyToneMapping) finalColor = tonemap(finalColor);\n" +
                "   color = vec4(finalColor, 1.0);\n"
    ).add(tonemapGLSL).add(sampleSkyboxForAmbient)

    fun getCombineLightShader(settingsV2: DeferredSettings): Shader {
        val useMSAA = useMSAA
        val code = if (useMSAA) -1 else -2
        return shaderCache.getOrPut(settingsV2 to code) {

            val builder = ShaderBuilder("CombineLights")
            builder.addVertex(combineVStage)
            val fragment = combineFStage
            // deferred inputs
            // find deferred layers, which exist, and appear in the shader
            val deferredCode = StringBuilder()
            deferredCode.append(
                if (useMSAA) {
                    "" +
                            "ambientOcclusion = texture(occlusionTex,uv).x;\n" +
                            "ivec2 iuv = ivec2(uv*textureSize(lightTex));\n" +
                            "finalMetallic = dot(metallicMask, texelFetch(metallicTex,iuv,gl_SampleID));\n" +
                            "finalLight = texelFetch(lightTex,iuv,0).rgb;\n"
                } else {
                    "" +
                            "ambientOcclusion = texture(occlusionTex,uv).x;\n" +
                            "finalMetallic = dot(metallicMask, texture(metallicTex,uv));\n" +
                            "finalLight = texture(lightTex,uv).rgb;\n"
                }
            )
            val sampleVariableName = if (useMSAA) "gl_SampleID" else null
            val samplerType = if (useMSAA) GLSLType.S2DMS else GLSLType.S2D
            val deferredVariables = ArrayList<Variable>()
            deferredVariables += Variable(GLSLType.V2F, "uv")
            deferredVariables += Variable(GLSLType.S2D, "occlusionTex")
            deferredVariables += Variable(samplerType, "metallicTex")
            deferredVariables += Variable(GLSLType.V4F, "metallicMask")
            deferredVariables += Variable(samplerType, "lightTex")
            deferredVariables += Variable(GLSLType.V3F, "finalLight", VariableMode.OUT)
            deferredVariables += Variable(GLSLType.V1F, "ambientOcclusion", VariableMode.OUT)
            deferredVariables += Variable(GLSLType.V1F, "finalMetallic", VariableMode.OUT)
            val imported = HashSet<String>()
            for (layer in settingsV2.layers) {
                // if this layer is present,
                // then define the output,
                // and write the mapping
                val glslName = layer.type.glslName
                if (fragment.variables.any2 { it.name == glslName }) {
                    val lType = layer.type
                    deferredVariables.add(Variable(floats[lType.workDims - 1], lType.glslName, VariableMode.OUT))
                    layer.appendMapping(deferredCode, "", "Tmp", "", "uv", imported, sampleVariableName)
                }
            }
            deferredVariables += imported.map { Variable(samplerType, it, VariableMode.IN) }
            val deferredStage = ShaderStage("deferred", deferredVariables, deferredCode.toString())
            deferredStage.add(octNormalPacking)
            builder.addFragment(deferredStage)
            builder.addFragment(fragment)
            if (useMSAA) builder.glslVersion = 400 // required for gl_SampleID
            val shader = builder.create("cmb0")
            // find all textures
            // first the ones for the deferred data
            // then the ones for the shadows
            shader.ignoreNameWarnings(
                "tint", "invLocalTransform", "d_camRot", "d_fovFactor",
                "defLayer0", "defLayer1", "defLayer2", "defLayer3",
                "defLayer4", "defLayer5", "defLayer6", "defLayer7"
            )
            shader
        }
    }

    var countPerPixel = 0.25f

    val vertexI = ShaderStage(
        "v", listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.V4F, "instanceTrans0", VariableMode.ATTR),
            Variable(GLSLType.V4F, "instanceTrans1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "instanceTrans2", VariableMode.ATTR),
            Variable(GLSLType.V4F, "invInsTrans0", VariableMode.ATTR),
            Variable(GLSLType.V4F, "invInsTrans1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "invInsTrans2", VariableMode.ATTR),
            Variable(GLSLType.V3F, "lightData0", VariableMode.ATTR),
            Variable(GLSLType.V1F, "lightData1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "shadowData", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.V1B, "isDirectional"),
            Variable(GLSLType.V4F, "invInsTrans0v", VariableMode.OUT).flat(),
            Variable(GLSLType.V4F, "invInsTrans1v", VariableMode.OUT).flat(),
            Variable(GLSLType.V4F, "invInsTrans2v", VariableMode.OUT).flat(),
            Variable(GLSLType.V4F, "data0", VariableMode.OUT).flat(),
            Variable(GLSLType.V1F, "data1", VariableMode.OUT).flat(),
            Variable(GLSLType.V4F, "data2", VariableMode.OUT).flat(),
            Variable(GLSLType.V3F, "uvw", VariableMode.OUT),
        ), "" +
                // todo if is spot light, we need to extend/shrink the cone
                "data0 = vec4(lightData0.rgb,0.0);\n" +
                "data1 = lightData1;\n" +
                "data2 = shadowData;\n" +
                // cutoff = 0 -> scale onto the whole screen, has effect everywhere
                "if(isDirectional && data2.a <= 0.0){\n" +
                "   gl_Position = vec4(coords.xy, 0.5, 1.0);\n" +
                "} else {\n" +
                "   mat4x3 localTransform = loadMat4x3(instanceTrans0,instanceTrans1,instanceTrans2);\n" +
                "   vec3 globalPos = matMul(localTransform, vec4(coords, 1.0));\n" +
                "   gl_Position = matMul(transform, vec4(globalPos, 1.0));\n" +
                "}\n" +
                "invInsTrans0v = invInsTrans0;\n" +
                "invInsTrans1v = invInsTrans1;\n" +
                "invInsTrans2v = invInsTrans2;\n" +
                "uvw = gl_Position.xyw;\n"
    ).add(loadMat4x3)

    val vertexNI = ShaderStage(
        "v", listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.M4x3, "localTransform"),
            Variable(GLSLType.V1F, "cutoff"),
            Variable(GLSLType.V3F, "uvw", VariableMode.OUT)
        ), "" +
                // todo if is spot light, we need to extend/shrink the cone
                // cutoff = 0 -> scale onto the whole screen, has effect everywhere
                "if(cutoff <= 0.0){\n" +
                "   gl_Position = vec4(coords.xy, 0.5, 1.0);\n" +
                "} else {\n" +
                "   vec3 globalPos = matMul(localTransform, vec4(coords, 1.0));\n" +
                "   gl_Position = matMul(transform, vec4(globalPos, 1.0));\n" +
                "}\n" +
                "uvw = gl_Position.xyw;\n"
    )

    private val shaderCache = HashMap<Pair<DeferredSettings, Int>, Shader>()

    fun createMainFragmentStage(type: LightType, isInstanced: Boolean): ShaderStage {
        val ws = !isInstanced // with shadows
        val co = "discard" // cutoff keyword
        val coreFragment = LightType.getShaderCode(type, co, ws)
        val fragment = ShaderStage(
            "ls-f", listOf(
                Variable(GLSLType.V4F, "data0"),
                Variable(GLSLType.V1F, "data1"),
                Variable(GLSLType.V4F, "data2"), // only if with shadows
                // light maps for shadows
                // - spotlights, directional lights
                Variable(GLSLType.S2DAShadow, "shadowMapPlanar", Renderers.MAX_PLANAR_LIGHTS),
                // - point lights
                Variable(GLSLType.SCubeShadow, "shadowMapCubic", 1),
                Variable(GLSLType.V1B, "receiveShadows"),
                Variable(GLSLType.V3F, "finalPosition"),
                Variable(GLSLType.V3F, "finalNormal"),
                Variable(GLSLType.V1F, "finalMetallic"),
                Variable(GLSLType.V1F, "finalRoughness"),
                Variable(GLSLType.V1F, "finalSheen"),
                Variable(GLSLType.V1F, "finalTranslucency"),
                Variable(GLSLType.M4x3, "camSpaceToLightSpace"), // invLightMatrices[i]
                Variable(GLSLType.M4x3, "lightSpaceToCamSpace"), // lightMatrices[i]
                Variable(GLSLType.V3F, "cameraPosition"),
                Variable(GLSLType.V1F, "worldScale"),
                Variable(GLSLType.V4F, "light", VariableMode.OUT)
            ) + depthVars, "" +
                    // light calculation including shadows if !instanced
                    "vec3 diffuseLight = vec3(0.0), specularLight = vec3(0.0);\n" +
                    "bool hasSpecular = finalMetallic > 0.0;\n" +
                    "float reflectivity = finalMetallic * (1.0 - finalRoughness);\n" +
                    "vec3 V = -normalize(rawCameraDirection(uv));\n" +
                    "float NdotV = dot(finalNormal,V);\n" +
                    "int shadowMapIdx0 = 0;\n" + // always 0 at the start
                    "int shadowMapIdx1 = int(data2.g);\n" +
                    // light properties, which are typically inside the loop
                    "vec3 lightColor = data0.rgb;\n" +
                    "vec3 lightPos = matMul(camSpaceToLightSpace, vec4(finalPosition, 1.0));\n" +
                    "vec3 lightNor = normalize(matMul(camSpaceToLightSpace, vec4(finalNormal, 0.0)));\n" +
                    "vec3 viewDir = normalize(matMul(camSpaceToLightSpace, vec4(finalPosition, 0.0)));\n" +
                    "float NdotL = 0.0;\n" + // normal dot light
                    "vec3 effectiveDiffuse = vec3(0.0), effectiveSpecular = vec3(0.0), lightDir = vec3(0.0);\n" +
                    "float shaderV0 = data1, shaderV1 = data2.z, shaderV2 = data2.w;\n" +
                    coreFragment +
                    "if(hasSpecular && NdotL > 0.0001 && NdotV > 0.0001){\n" +
                    "   vec3 lightDirWS = normalize(matMul(lightSpaceToCamSpace,vec4(lightDir,0.0)));\n" +
                    "   vec3 H = normalize(V + lightDirWS);\n" +
                    specularBRDFv2NoColorStart +
                    specularBRDFv2NoColor +
                    "   specularLight = effectiveSpecular * computeSpecularBRDF;\n" +
                    specularBRDFv2NoColorEnd +
                    "}\n" +
                    // translucency; looks good and approximately correct
                    // sheen is a fresnel effect, which adds light at the edge, e.g., for clothing
                    "NdotL = mix(NdotL, $translucencyNL, finalTranslucency) + finalSheen;\n" +
                    "diffuseLight += effectiveDiffuse * clamp(NdotL, 0.0, 1.0);\n" +
                    // ~65k is the limit, after that only Infinity
                    // todo car sample's light on windows looks clamped... who is clamping it?
                    "vec3 color = mix(diffuseLight, specularLight, finalMetallic);\n" +
                    // "light = vec4(fract(-0.01 + finalPosition/worldScale + cameraPosition), 1.0);\n"
                    "light = vec4(clamp(color, 0.0, 16e3), 1.0);\n"
        )
        fragment.add(quatRot)
        return fragment
    }

    val translucencyNL = 0.5f

    val visualizeLightCountShader = Shader(
        "visualize-light-count",
        listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.M4x3, "localTransform"),
            Variable(GLSLType.V1B, "fullscreen"),
        ), "" +
                "void main(){\n" +
                // cutoff = 0 -> scale onto the whole screen, has effect everywhere
                "   if(fullscreen){\n" +
                "      gl_Position = vec4(coords.xy, 0.5, 1.0);\n" +
                "   } else {\n" +
                "      gl_Position = matMul(transform, vec4(matMul(localTransform, vec4(coords, 1.0)), 1.0));\n" +
                "   }\n" +
                "}\n", emptyList(), listOf(
            Variable(GLSLType.V1F, "countPerPixel"),
            Variable(GLSLType.V4F, "result", VariableMode.OUT)
        ), "void main(){ result = vec4(countPerPixel); }"
    ).apply {
        ignoreNameWarnings(
            "cameraPosition", "cameraRotation", "invScreenSize", "receiveShadows",
            "d_camRot", "d_fovFactor", "data0", "data1", "data2", "cutoff",
            "lightSpaceToCamSpace", "camSpaceToLightSpace", "worldScale", "isDirectional"
        )
    }

    val visualizeLightCountShaderInstanced = Shader(
        "visualize-light-count-instanced", listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.V4F, "instanceTrans0", VariableMode.ATTR),
            Variable(GLSLType.V4F, "instanceTrans1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "instanceTrans2", VariableMode.ATTR),
            Variable(GLSLType.V4F, "shadowData", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.V1B, "isDirectional"),
        ), "" +
                loadMat4x3 +
                "void main(){\n" +
                // cutoff = 0 -> scale onto the whole screen, has effect everywhere
                "   if(isDirectional && shadowData.a <= 0.0){\n" +
                "      gl_Position = vec4(coords.xy, 0.5, 1.0);\n" +
                "   } else {\n" +
                "       mat4x3 localTransform = loadMat4x3(instanceTrans0,instanceTrans1,instanceTrans2);\n" +
                "      gl_Position = matMul(transform, vec4(matMul(localTransform, vec4(coords, 1.0)), 1.0));\n" +
                "   }\n" +
                "}", emptyList(), listOf(
            Variable(GLSLType.V1F, "countPerPixel"),
            Variable(GLSLType.V4F, "result", VariableMode.OUT)
        ), "" +
                "void main(){ result = vec4(countPerPixel); }"
    ).apply {
        ignoreNameWarnings(
            "normals", "uvs", "tangents", "colors", "receiveShadows",
            "invScreenSize", "cameraPosition", "cameraRotation", "d_camRot", "d_fovFactor", "worldScale",
            "data0", "data1", "data2", "cutoff", "lightSpaceToCamSpace", "camSpaceToLightSpace",
            "receiveShadows", "isDirectional"
        )
    }

    val uvwStage = ShaderStage(
        "uv2uvw", listOf(
            Variable(GLSLType.V3F, "uvw", VariableMode.IN),
            Variable(GLSLType.V2F, "uv", VariableMode.OUT),
            Variable(GLSLType.V2F, "invScreenSize")
        ), "uv = gl_FragCoord.xy * invScreenSize;\n"
    )

    val invStage = ShaderStage(
        "invTrans2cs2ls", listOf(
            Variable(GLSLType.V4F, "invInsTrans0v", VariableMode.IN).flat(),
            Variable(GLSLType.V4F, "invInsTrans1v", VariableMode.IN).flat(),
            Variable(GLSLType.V4F, "invInsTrans2v", VariableMode.IN).flat(),
            Variable(GLSLType.M4x3, "camSpaceToLightSpace", VariableMode.OUT)
        ), "camSpaceToLightSpace = loadMat4x3(invInsTrans0v,invInsTrans1v,invInsTrans2v);\n"
    ).add(loadMat4x3)

    fun getShader(settingsV2: DeferredSettings, type: LightType): Shader {
        val isInstanced = GFXState.instanceData.currentValue != MeshInstanceData.DEFAULT
        val useMSAA = useMSAA
        val key = type.ordinal * 4 + useMSAA.toInt(2) + isInstanced.toInt()
        return shaderCache.getOrPut(settingsV2 to key) {

            val builder = ShaderBuilder("Light-$type-$isInstanced")
            builder.addVertex(if (isInstanced) vertexI else vertexNI)
            if (isInstanced) builder.addFragment(invStage)
            builder.addFragment(uvwStage)
            builder.addFragment(
                ShaderStage(
                    "uv2depth",
                    listOf(
                        Variable(GLSLType.V2F, "uv"),
                        Variable(if (useMSAA) GLSLType.S2DMS else GLSLType.S2D, "depthTex"),
                        Variable(GLSLType.V3F, "finalPosition", VariableMode.OUT)
                    ), if (useMSAA) {
                        "finalPosition = rawDepthToPosition(uv,texelFetch(depthTex,ivec2(uv*vec2(textureSize(depthTex))),gl_SampleID).x);\n"
                    } else {
                        "finalPosition = rawDepthToPosition(uv,texture(depthTex,uv).x);\n"
                    }
                )
            )
            val fragment = createMainFragmentStage(type, isInstanced)
            // deferred inputs: find deferred layers, which exist, and appear in the shader
            val deferredCode = StringBuilder()
            val deferredVariables = ArrayList<Variable>()
            deferredVariables += Variable(GLSLType.V2F, "uv")
            val imported = HashSet<String>()
            val sampleVariableName = if (useMSAA) "gl_SampleID" else null
            val samplerType = if (useMSAA) GLSLType.S2DMS else GLSLType.S2D
            for (layer in settingsV2.layers) {
                // if this layer is present,
                // then define the output,
                // and write the mapping
                val glslName = layer.type.glslName
                if (fragment.variables.any2 { it.name == glslName }) {
                    val lType = layer.type
                    deferredVariables.add(Variable(floats[lType.workDims - 1], lType.glslName, VariableMode.OUT))
                    layer.appendMapping(deferredCode, "", "Tmp", "", "uv", imported, sampleVariableName)
                }
            }
            deferredVariables += imported.map { Variable(samplerType, it, VariableMode.IN) }
            deferredVariables += depthVars
            val deferredStage = ShaderStage("deferred", deferredVariables, deferredCode.toString())
            deferredStage
                .add(rawToDepth)
                .add(depthToPosition)
                .add(quatRot)
                .add(octNormalPacking)
            builder.addFragment(deferredStage)
            builder.addFragment(fragment)
            if (useMSAA) builder.glslVersion = 400 // required for gl_SampleID
            val shader = builder.create("lht${type.ordinal}")
            shader.ignoreNameWarnings(
                "tint", "invLocalTransform", "colors",
                "tangents", "uvs", "normals", "isDirectional",
                "defLayer0", "defLayer1", "defLayer2", "defLayer3", "defLayer4",
                "receiveShadows", "countPerPixel", "worldScale", "cameraPosition", "invScreenSize",
                "fullscreen", "prevLocalTransform", "data1", "cameraRotation"
            )
            shader
        }
    }
}