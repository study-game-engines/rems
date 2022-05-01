package me.anno.ecs.components.camera.effects

import me.anno.ecs.prefab.PrefabSaveable
import me.anno.gpu.deferred.DeferredLayerType

abstract class ToneMappedEffect : CameraEffect() {

    var applyToneMapping = false
    val dstType get() = if (applyToneMapping) DeferredLayerType.SDR_RESULT else DeferredLayerType.HDR_RESULT

    override fun listOutputs() = if (applyToneMapping) sdrOutputs else hdrOutputs

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as ToneMappedEffect
        clone.applyToneMapping = applyToneMapping
    }

    companion object {
        private val sdrOutputs = listOf(DeferredLayerType.SDR_RESULT)
        private val hdrOutputs = listOf(DeferredLayerType.HDR_RESULT)
    }
}