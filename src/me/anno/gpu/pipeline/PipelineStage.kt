package me.anno.gpu.pipeline

import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.Transform
import me.anno.ecs.components.cache.MaterialCache
import me.anno.ecs.components.light.DirectionalLight
import me.anno.ecs.components.light.LightType
import me.anno.ecs.components.light.PointLight
import me.anno.ecs.components.light.SpotLight
import me.anno.ecs.components.mesh.Material
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.Mesh.Companion.defaultMaterial
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.engine.ui.render.RenderView
import me.anno.engine.ui.render.Renderers
import me.anno.gpu.DepthMode
import me.anno.gpu.GFX
import me.anno.gpu.GFX.shaderColor
import me.anno.gpu.RenderState
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.buffer.Attribute
import me.anno.gpu.buffer.AttributeType
import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.pipeline.M4x3Delta.buffer16x256
import me.anno.gpu.pipeline.M4x3Delta.m4x3delta
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.Shader
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.input.Input.isKeyDown
import me.anno.io.Saveable
import me.anno.utils.maths.Maths.clamp
import me.anno.utils.maths.Maths.min
import me.anno.utils.structures.maps.KeyPairMap
import me.anno.utils.types.AABBs.clear
import me.anno.utils.types.AABBs.transformUnion
import org.joml.*
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL21

class PipelineStage(
    var name: String,
    var sorting: Sorting,
    var maxNumberOfLights: Int,
    var blendMode: BlendMode?,
    var depthMode: DepthMode,
    var writeDepth: Boolean,
    var cullMode: Int, // 0 = both, gl_front, gl_back
    var defaultShader: BaseShader
) : Saveable() {

    companion object {

        val lastMaterial = HashMap<Shader, Material>(64)
        private val tmp3x3 = Matrix3f()

        // is rotation, position and scale enough?...
        private val meshInstancedAttributes = listOf(
            Attribute("instanceTrans0", 4),
            Attribute("instanceTrans1", 4),
            Attribute("instanceTrans2", 4),
            Attribute("instanceTint", AttributeType.UINT8_NORM, 4)
        )

        const val instancedBatchSize = 1024

        val meshInstanceBuffer = StaticBuffer(meshInstancedAttributes, instancedBatchSize, GL_DYNAMIC_DRAW)

        val tmpAABBd = AABBd()

        fun getDrawMatrix(transform: Transform, time: Long): Matrix4x3d {
            val drawTransform = transform.drawTransform
            val factor = transform.updateDrawingLerpFactor(time)
            if (factor > 0.0) {
                val extrapolatedTime = (GFX.gameTime - transform.lastUpdateTime).toDouble() / transform.lastUpdateDt
                // needs to be changed, if the extrapolated time changes -> it changes if the phyisics engine is behind
                // its target -> in the physics engine, we send the game time instead of the physics time,
                // and this way, it's relatively guaranteed to be roughly within [0,1]
                val fac2 = factor / (clamp(1.0 - extrapolatedTime, 0.001, 1.0))
                if (fac2 < 1.0) {
                    drawTransform.lerp(transform.globalTransform, fac2)
                    transform.checkDrawTransform()
                } else {
                    drawTransform.set(transform.globalTransform)
                    transform.checkDrawTransform()
                }
            }
            return drawTransform
        }

        fun setupLocalTransform(
            shader: Shader,
            transform: Transform,
            cameraPosition: Vector3d,
            worldScale: Double,
            time: Long
        ) {

            val drawTransform = getDrawMatrix(transform, time)
            shader.m4x3delta("localTransform", drawTransform, cameraPosition, worldScale)

            val invLocalUniform = shader["invLocalTransform"]
            if (invLocalUniform >= 0) {
                val invLocal = tmp3x3.set(
                    drawTransform.m00().toFloat(), drawTransform.m01().toFloat(), drawTransform.m02().toFloat(),
                    drawTransform.m10().toFloat(), drawTransform.m11().toFloat(), drawTransform.m12().toFloat(),
                    drawTransform.m20().toFloat(), drawTransform.m21().toFloat(), drawTransform.m22().toFloat(),
                ).invert()
                shader.m3x3(invLocalUniform, invLocal)
            }

        }

    }

    var nextInsertIndex = 0
    var instancedSize = 0
    val drawRequests = ArrayList<DrawRequest>()

    val size get() = nextInsertIndex + instancedSize

    val instancedMeshes = KeyPairMap<Mesh, Int, InstancedStack>()

    fun bindDraw(pipeline: Pipeline, cameraMatrix: Matrix4fc, cameraPosition: Vector3d, worldScale: Double) {
        RenderState.blendMode.use(blendMode) {
            RenderState.depthMode.use(depthMode) {
                RenderState.depthMask.use(writeDepth) {
                    RenderState.cullMode.use(cullMode) {
                        GFX.check()
                        draw(pipeline, cameraMatrix, cameraPosition, worldScale)
                        GFX.check()
                    }
                }
            }
        }
    }

    fun setupLights(
        pipeline: Pipeline, shader: Shader,
        cameraPosition: Vector3d, worldScale: Double,
        request: DrawRequest
    ) {
        setupLights(pipeline, shader, cameraPosition, worldScale, request.entity.aabb)
    }

    fun setupLights(
        pipeline: Pipeline, shader: Shader,
        cameraPosition: Vector3d, worldScale: Double,
        aabb: AABBd
    ) {
        val time = GFX.gameTime
        val numberOfLightsPtr = shader["numberOfLights"]
        // LOGGER.info("#0: $numberOfLightsPtr")
        if (numberOfLightsPtr < 0) return
        val maxNumberOfLights = RenderView.MAX_LIGHTS
        val lights = pipeline.lights
        val numberOfLights = pipeline.getClosestRelevantNLights(aabb, maxNumberOfLights, lights)
        shader.v1(numberOfLightsPtr, numberOfLights)
        if (numberOfLights > 0) {
            val invLightMatrices = shader["invLightMatrices"]
            val buffer = buffer16x256
            if (invLightMatrices >= 0) {
                // fill all transforms
                buffer.limit(12 * numberOfLights)
                for (i in 0 until numberOfLights) {
                    buffer.position(12 * i)
                    val light = lights[i]!!.light
                    light.invWorldMatrix.get(buffer)
                }
                buffer.position(0)
                GL21.glUniformMatrix4x3fv(invLightMatrices, false, buffer)
            }
            // and sharpness; implementation depending on type
            val lightIntensities = shader["lightData0"]
            if (lightIntensities >= 0) {
                // fill all light colors
                buffer.limit(4 * numberOfLights)
                for (i in 0 until numberOfLights) {
                    val light = lights[i]!!.light
                    val color = light.color
                    buffer.put(color.x)
                    buffer.put(color.y)
                    buffer.put(color.z)
                    val type = when (light) {
                        is DirectionalLight -> LightType.DIRECTIONAL.id
                        is PointLight -> LightType.POINT.id
                        is SpotLight -> LightType.SPOT.id
                        else -> -1
                    }
                    buffer.put(type + 0.25f)
                }
                buffer.position(0)
                GL20.glUniform4fv(lightIntensities, buffer)
            }
            // type, and cone angle (or other data, if required)
            // additional, whether we have a texture, and maybe other data
            val lightTypes = shader["lightData1"]
            if (lightTypes >= 0) {

                buffer.limit(4 * numberOfLights)
                for (i in 0 until numberOfLights) {

                    val lightI = lights[i]!!
                    val light = lightI.light
                    val m = getDrawMatrix(lightI.transform, time)

                    buffer.put(((m.m30() - cameraPosition.x) * worldScale).toFloat())
                    buffer.put(((m.m31() - cameraPosition.y) * worldScale).toFloat())
                    buffer.put(((m.m32() - cameraPosition.z) * worldScale).toFloat())
                    buffer.put(light.getShaderV0(m, worldScale))

                }
                buffer.flip()
                GL20.glUniform4fv(lightTypes, buffer)
            }
            val shadowData = shader["shadowData"]
            if (shadowData >= 0) {
                buffer.limit(4 * numberOfLights)
                // write all texture indices, and bind all shadow textures (as long as we have slots available)
                var planarSlot = 0
                var cubicSlot = 0
                val maxTextureIndex = 31
                val planarIndex0 = shader.getTextureIndex("shadowMapPlanar0")
                val cubicIndex0 = shader.getTextureIndex("shadowMapCubic0")
                val supportsPlanarShadows = planarIndex0 >= 0
                val supportsCubicShadows = cubicIndex0 >= 0
                if (planarIndex0 < 0) planarSlot = Renderers.MAX_PLANAR_LIGHTS
                if (cubicIndex0 < 0) cubicSlot = Renderers.MAX_CUBEMAP_LIGHTS
                if (supportsPlanarShadows || supportsCubicShadows) {
                    for (i in 0 until numberOfLights) {
                        buffer.position(4 * i)
                        val light = lights[i]!!.light
                        buffer.put(0f)
                        buffer.put(0f)
                        buffer.put(light.getShaderV1())
                        buffer.put(light.getShaderV2())
                        buffer.position(4 * i)
                        if (light.hasShadow) {
                            if (light is PointLight) {
                                buffer.put(cubicSlot.toFloat()) // start index
                                if (cubicSlot < Renderers.MAX_CUBEMAP_LIGHTS) {
                                    val cascades = light.shadowTextures ?: continue
                                    val slot = cubicIndex0 + cubicSlot
                                    if (slot > maxTextureIndex) continue
                                    val texture = cascades[0].depthTexture!!
                                    // bind the texture, and don't you dare to use mipmapping ^^
                                    // (at least without variance shadow maps)
                                    texture.bind(slot, GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
                                    cubicSlot++ // no break necessary
                                }
                                buffer.put(cubicSlot.toFloat()) // end index
                            } else {
                                buffer.put(planarSlot.toFloat()) // start index
                                if (planarSlot < Renderers.MAX_PLANAR_LIGHTS) {
                                    val cascades = light.shadowTextures ?: continue
                                    for (j in cascades.indices) {
                                        val slot = planarIndex0 + planarSlot
                                        if (slot > maxTextureIndex) break
                                        val texture = cascades[j].depthTexture!!
                                        // bind the texture, and don't you dare to use mipmapping ^^
                                        // (at least without variance shadow maps)
                                        texture.bind(slot, GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
                                        if (++planarSlot >= Renderers.MAX_PLANAR_LIGHTS) break
                                    }
                                }
                                buffer.put(planarSlot.toFloat()) // end index
                            }
                        }
                    }
                }
                buffer.position(0)
                GL20.glUniform4fv(shadowData, buffer)
            }
        }
    }

    fun initShader(shader: Shader, cameraMatrix: Matrix4fc, pipeline: Pipeline, visualizeLightCount: Int) {
        // information for the shader, which is material agnostic
        // add all things, the shader needs to know, e.g. light direction, strength, ...
        // (for the cheap shaders, which are not deferred)
        shader.m4x4("transform", cameraMatrix)
        shader.v3("ambientLight", pipeline.ambient)
        shader.v1("visualizeLightCount", visualizeLightCount)
    }

    fun draw(pipeline: Pipeline, cameraMatrix: Matrix4fc, cameraPosition: Vector3d, worldScale: Double) {

        // the dotViewDir may be easier to calculate, and technically more correct, but it has one major flaw:
        // it changes when the cameraDirection is changing. This ofc is not ok, since it would resort the entire list,
        // and that's expensive

        // todo sorting function, that also uses the materials, so we need to switch seldom?
        // todo and light groups, so we don't need to update lights that often

        // val viewDir = pipeline.frustum.cameraRotation.transform(Vector3d(0.0, 0.0, 1.0))
        when (sorting) {
            Sorting.NO_SORTING -> {
            }
            Sorting.FRONT_TO_BACK -> {
                drawRequests.sortBy {
                    // - it.entity.transform.dotViewDir(cameraPosition, viewDir)
                    it.entity.transform.distanceSquaredGlobally(cameraPosition)
                }
            }
            Sorting.BACK_TO_FRONT -> {
                drawRequests.sortByDescending {
                    // - it.entity.transform.dotViewDir(cameraPosition, viewDir)
                    it.entity.transform.distanceSquaredGlobally(cameraPosition)
                }
            }
        }

        var lastEntity: Entity? = null
        var lastMesh: Mesh? = null
        var lastShader: Shader? = null

        val time = GFX.gameTime

        // we could theoretically cluster them to need fewer uploads
        // but that would probably be quite hard to implement reliably
        val hasLights = maxNumberOfLights > 0
        val needsLightUpdateForEveryMesh = hasLights && pipeline.lightPseudoStage.size > maxNumberOfLights

        pipeline.lights.fill(null)

        val visualizeLightCount = if (isKeyDown('t')) 1 else 0

        // draw non-instanced meshes
        var previousMaterialInScene: Material? = null
        for (index in 0 until nextInsertIndex) {

            val request = drawRequests[index]
            val mesh = request.mesh
            val entity = request.entity

            GFX.drawnId = request.clickId

            val transform = entity.transform

            val materialIndex = request.materialIndex
            val material = getMaterial(mesh, materialIndex)
            val shader = getShader(material)
            shader.use()

            val renderer = request.component

            val previousMaterialByShader = lastMaterial.put(shader, material)
            if (previousMaterialByShader == null) {
                initShader(shader, cameraMatrix, pipeline, visualizeLightCount)
            }

            if (hasLights) {
                if (previousMaterialByShader == null ||
                    needsLightUpdateForEveryMesh
                ) {
                    // upload all light data
                    setupLights(pipeline, shader, cameraPosition, worldScale, request)
                }
            }

            setupLocalTransform(shader, transform, cameraPosition, worldScale, time)

            // the state depends on textures (global) and uniforms (per shader),
            // so test both
            if (previousMaterialByShader != material || previousMaterialInScene != material) {
                // bind textures for the material
                // bind all default properties, e.g. colors, roughness, metallic, clear coat/sheen, ...
                material.defineShader(shader)
                previousMaterialInScene = material
            }


            mesh.ensureBuffer()

            // only if the entity or mesh changed
            // not if the material has changed
            // this updates the skeleton and such
            if (entity !== lastEntity || lastMesh !== mesh || lastShader !== shader) {
                if (renderer is MeshComponent && mesh.hasBonesInBuffer)
                    renderer.defineVertexTransform(shader, entity, mesh)
                else shader.v1("hasAnimation", false)
                lastEntity = entity
                lastMesh = mesh
                lastShader = shader
            }

            shaderColor(shader, "tint", -1)
            shader.v1("hasVertexColors", mesh.hasVertexColors)

            mesh.draw(shader, materialIndex)

        }

        lastMaterial.clear()

        // draw instanced meshes
        val batchSize = instancedBatchSize
        val buffer = meshInstanceBuffer
        val aabb = tmpAABBd
        RenderState.instanced.use(true) {
            for ((mesh, list) in instancedMeshes.values) {
                for ((materialIndex, values) in list) {
                    if (values.isNotEmpty()) {

                        mesh.ensureBuffer()

                        val localAABB = mesh.aabb
                        val material = getMaterial(mesh, materialIndex)

                        val shader = getShader(material)
                        shader.use()

                        // update material and light properties
                        val previousMaterial = lastMaterial.put(shader, material)
                        if (previousMaterial == null) {
                            initShader(shader, cameraMatrix, pipeline, visualizeLightCount)
                        }
                        if (previousMaterial == null && !needsLightUpdateForEveryMesh) {
                            aabb.clear()
                            pipeline.frustum.union(aabb)
                            setupLights(pipeline, shader, cameraPosition, worldScale, aabb)
                        }
                        material.defineShader(shader)
                        shaderColor(shader, "tint", -1)
                        shader.v1("drawMode", GFX.drawMode.id)
                        shader.v1("hasAnimation", false)
                        shader.v1("hasVertexColors", mesh.hasVertexColors)
                        GFX.check()
                        // draw them in batches of size <= batchSize
                        val size = values.size
                        for (baseIndex in 0 until size step batchSize) {
                            buffer.clear()
                            val nioBuffer = buffer.nioBuffer!!
                            // fill the data
                            val trs = values.transforms
                            val ids = values.clickIds
                            for (index in baseIndex until min(size, baseIndex + batchSize)) {
                                m4x3delta(getDrawMatrix(trs[index]!!, time), cameraPosition, worldScale, nioBuffer, false)
                                buffer.putInt(ids[index])
                            }
                            if (needsLightUpdateForEveryMesh) {
                                // calculate the lights for each group
                                // todo cluster them cheaply?
                                aabb.clear()
                                for (index in baseIndex until min(size, baseIndex + batchSize)) {
                                    localAABB.transformUnion(trs[index]!!.drawTransform, aabb)
                                }
                                setupLights(pipeline, shader, cameraPosition, worldScale, aabb)
                            }
                            GFX.check()
                            mesh.drawInstanced(shader, materialIndex, buffer)
                        }
                    }
                }
            }
        }
        lastMaterial.clear()

    }

    fun drawDepth(pipeline: Pipeline, cameraMatrix: Matrix4fc, cameraPosition: Vector3d, worldScale: Double) {

        var lastEntity: Entity? = null
        var lastMesh: Mesh? = null

        val time = GFX.gameTime

        val shader = defaultShader.value
        shader.use()

        initShader(shader, cameraMatrix, pipeline, 0)

        // draw non-instanced meshes
        for (index in 0 until nextInsertIndex) {

            val request = drawRequests[index]
            val mesh = request.mesh
            val entity = request.entity

            val transform = entity.transform
            val renderer = request.component

            setupLocalTransform(shader, transform, cameraPosition, worldScale, time)

            mesh.ensureBuffer()

            // only if the entity or mesh changed
            // not if the material has changed
            // this updates the skeleton and such
            if (entity !== lastEntity || lastMesh !== mesh) {
                if (renderer is MeshComponent && mesh.hasBonesInBuffer)
                    renderer.defineVertexTransform(shader, entity, mesh)
                else shader.v1("hasAnimation", false)
                lastEntity = entity
                lastMesh = mesh
            }

            shaderColor(shader, "tint", -1)
            shader.v1("hasVertexColors", mesh.hasVertexColors)

            mesh.drawDepth(shader)

        }

        GFX.check()

        // draw instanced meshes
        val batchSize = instancedBatchSize
        val buffer = meshInstanceBuffer
        RenderState.instanced.use(true) {
            val shader2 = defaultShader.value
            shader2.use()
            initShader(shader2, cameraMatrix, pipeline, 0)
            for ((mesh, list) in instancedMeshes.values) {
                for ((_, values) in list) {
                    if (values.isNotEmpty()) {
                        mesh.ensureBuffer()
                        shader2.v1("hasAnimation", false)
                        shader2.v1("hasVertexColors", mesh.hasVertexColors)
                        val size = values.size
                        for (baseIndex in 0 until size step batchSize) {
                            buffer.clear()
                            val nioBuffer = buffer.nioBuffer!!
                            // fill the data
                            val trs = values.transforms
                            for (index in baseIndex until min(size, baseIndex + batchSize)) {
                                m4x3delta(getDrawMatrix(trs[index]!!, time), cameraPosition, worldScale, nioBuffer, false)
                                buffer.putInt(0) // clickId
                            }
                            buffer.ensureBufferWithoutResize()
                            mesh.drawInstancedDepth(shader2, buffer)
                        }
                    }
                }
            }
        }

        GFX.check()

    }

    private var hadTooMuchSpace = 0
    fun reset() {

        // there is too much space since 100 iterations
        if (nextInsertIndex < drawRequests.size shr 1) {
            if (hadTooMuchSpace++ > 100) {
                drawRequests.clear()
            }
        } else hadTooMuchSpace = 0

        nextInsertIndex = 0
        instancedSize = 0
        for ((_, values) in instancedMeshes.values) {
            for ((_, value) in values) {
                value.clear()
            }
        }

    }

    fun add(component: Component, mesh: Mesh, entity: Entity, materialIndex: Int, clickId: Int) {
        if (nextInsertIndex >= drawRequests.size) {
            val request = DrawRequest(mesh, component, entity, materialIndex, clickId)
            drawRequests.add(request)
        } else {
            val request = drawRequests[nextInsertIndex]
            request.mesh = mesh
            request.component = component
            request.entity = entity
            request.materialIndex = materialIndex
            request.clickId = clickId
        }
        nextInsertIndex++
    }

    fun addInstanced(mesh: Mesh, entity: Entity, materialIndex: Int, clickId: Int) {
        val stack = instancedMeshes.getOrPut(mesh, materialIndex) { _, _ -> InstancedStack() }
        // instanced animations not supported (entity not saved); they would need to be the same, and that's probably very rare...
        stack.add(entity.transform, clickId)
        instancedSize++
    }

    fun getMaterial(mesh: Mesh, index: Int): Material {
        return MaterialCache[mesh.materials.getOrNull(index), defaultMaterial]
    }

    fun getShader(material: Material): Shader {
        return (material.shader ?: defaultShader).value
    }

    override val className: String = "PipelineStage"
    override val approxSize: Int = 5
    override fun isDefaultValue(): Boolean = false

}