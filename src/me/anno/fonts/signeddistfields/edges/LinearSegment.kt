package me.anno.fonts.signeddistfields.edges

import me.anno.fonts.signeddistfields.algorithm.SDFMaths.absDotNormalized
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.absDotNormalizedXYY
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.crossProduct
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.dotProduct
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.getOrthonormal
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.nonZeroSign
import me.anno.fonts.signeddistfields.structs.FloatPtr
import me.anno.fonts.signeddistfields.structs.SignedDistance
import me.anno.utils.pooling.JomlPools
import me.anno.utils.types.Vectors.minus
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector2fc
import kotlin.math.abs

class LinearSegment(val p0: Vector2fc, val p1: Vector2fc) : EdgeSegment() {

    override fun point(param: Float, dst: Vector2f) = dst.set(p0).lerp(p1, param)

    override fun direction(param: Float, dst: Vector2f): Vector2f = dst.set(p1).sub(p0)

    override fun length(): Float = p1.distance(p0)

    override fun toString() = "[$p0 $p1]"

    override fun union(bounds: AABBf, tmp: FloatArray) {
        bounds.union(p0.x(), p0.y(), 0f)
        bounds.union(p1.x(), p1.y(), 0f)
    }

    override fun signedDistance(origin: Vector2fc, param: FloatPtr, dst: SignedDistance): SignedDistance {
        val aq = JomlPools.vec2f.create()
        val ab = JomlPools.vec2f.create()
        val orthoNormal = JomlPools.vec2f.create()
        aq.set(origin).sub(p0)
        ab.set(p1).sub(p0)
        param.value = dotProduct(aq, ab) / ab.lengthSquared()
        val eqRef = if (param.value > 0.5) p1 else p0
        val endpointDistance = eqRef.distance(origin)
        if (param.value > 0 && param.value < 1) {
            ab.getOrthonormal(false, allowZero = false, orthoNormal)
            val orthoDistance = dotProduct(orthoNormal, aq)
            if (abs(orthoDistance) < endpointDistance) {
                JomlPools.vec2f.sub(3)
                return dst.set(orthoDistance, 0f)
            }// else should not happen, if I understand this correctly...
        }
        dst.set(
            nonZeroSign(crossProduct(aq, ab)) * endpointDistance,
            absDotNormalizedXYY(ab, eqRef, origin)
        )
        JomlPools.vec2f.sub(3)
        return dst
    }

}