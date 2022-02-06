package me.anno.fonts.signeddistfields.edges

import me.anno.fonts.signeddistfields.Flags.MSDFGEN_CUBIC_SEARCH_STARTS
import me.anno.fonts.signeddistfields.Flags.MSDFGEN_CUBIC_SEARCH_STEPS
import me.anno.fonts.signeddistfields.algorithm.EquationSolver.solveQuadratic
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.absDotNormalized
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.absDotNormalizedXYY
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.crossProduct
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.crossProductXYY
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.mix
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.nonZeroSign
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.union
import me.anno.fonts.signeddistfields.structs.FloatPtr
import me.anno.fonts.signeddistfields.structs.SignedDistance
import me.anno.utils.pooling.JomlPools
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector2fc
import kotlin.math.abs

/**
 * adapted from Multi Channel Signed Distance fields
 * */
class CubicSegment(
    val p0: Vector2fc,
    p10: Vector2fc,
    p20: Vector2fc,
    val p3: Vector2fc
) : EdgeSegment() {

    val p1 = if ((p10 == p0 || p10 == p3) && (p20 == p0 || p20 == p3)) mix(p0, p3, 1f / 3f) else p10
    val p2 = if ((p10 == p0 || p10 == p3) && (p20 == p0 || p20 == p3)) mix(p0, p3, 2f / 3f) else p20

    override fun toString() = "[$p0 $p1 $p2 $p3]"

    override fun point(param: Float, dst: Vector2f): Vector2f {
        val b = 1f - param
        val b2 = b * b
        val pr2 = param * param
        val aaa = pr2 * param
        val aab = 3f * pr2 * b
        val abb = 3f * param * b2
        val bbb = b * b2
        return dst.set(p0).mul(bbb)
            .add(p1.x() * abb, p1.y() * abb)
            .add(p2.x() * aab, p2.y() * aab)
            .add(p3.x() * aaa, p3.y() * aaa)
    }

    override fun direction(param: Float, dst: Vector2f): Vector2f {
        val b = 1f - param
        val a2 = param * param
        val ab2 = 2f * param * b
        val b2 = b * b
        val f1 = b2 - ab2
        val f2 = ab2 - a2
        dst.set(p0).mul(-b2)
            .add(p1.x() * f1, p1.y() * f1)
            .add(p2.x() * f2, p2.y() * f2)
            .add(p3.x() * a2, p3.y() * a2)
        if (dst.lengthSquared() == 0f) {
            if (param == 0f) return dst.set(p2).sub(p0)
            if (param == 1f) return dst.set(p3).sub(p1)
        }
        return dst
    }

    override fun length(): Float {
        throw RuntimeException("length() not implemented")
    }

    override fun union(bounds: AABBf, tmp: FloatArray) {

        union(bounds, p0)
        union(bounds, p3)

        val a0 = JomlPools.vec2f.create()
        val a1 = JomlPools.vec2f.create()
        val a2 = JomlPools.vec2f.create()

        a0.set(p1).sub(p0)
        a1.set(p2).sub(p1).sub(a0).mul(2f)
        a2.set(p1).sub(p2).mul(3f).add(p3).sub(p0)

        val tmpV2 = JomlPools.vec2f.create()

        var solutions = solveQuadratic(tmp, a2.x, a1.x, a0.x)
        for (i in 0 until solutions) {
            val tmpI = tmp[i]
            if (tmpI > 0f && tmpI < 1f)
                union(bounds, point(tmpI, tmpV2))
        }

        solutions = solveQuadratic(tmp, a2.y, a1.y, a0.y)
        for (i in 0 until solutions) {
            val tmpI = tmp[i]
            if (tmpI > 0f && tmpI < 1f)
                union(bounds, point(tmpI, tmpV2))
        }

        JomlPools.vec2f.sub(4)

    }

    override fun signedDistance(
        origin: Vector2fc,
        param: FloatPtr,
        tmp: FloatArray,
        dst: SignedDistance
    ): SignedDistance {

        val qa = JomlPools.vec2f.create()
        val ab = JomlPools.vec2f.create()
        val br = JomlPools.vec2f.create()
        val az = JomlPools.vec2f.create()
        val epDir = JomlPools.vec2f.create()
        val d1 = JomlPools.vec2f.create()
        val d2 = JomlPools.vec2f.create()
        val qe = JomlPools.vec2f.create()

        qa.set(p0).sub(origin)
        ab.set(p1).sub(p0)
        br.set(p2).sub(p1).sub(ab)
        az.set(p3).sub(p2).sub(p2).add(p1).sub(br)

        direction(0f, epDir)
        var minDistance = nonZeroSign(crossProduct(epDir, qa)) * qa.length() // distance from A

        param.value = -qa.dot(epDir) / epDir.lengthSquared()

        direction(1f, epDir)
        val distance = p3.distance(origin) // distance from B
        if (distance < abs(minDistance)) {
            minDistance = nonZeroSign(crossProductXYY(epDir, p3, origin)) * distance
            val dotProduct = epDir.lengthSquared() - p3.dot(epDir) + origin.dot(epDir)
            param.value = dotProduct / epDir.lengthSquared()
        }

        // Iterative minimum distance search
        for (i in 0..MSDFGEN_CUBIC_SEARCH_STARTS) {
            var t = i.toFloat() / MSDFGEN_CUBIC_SEARCH_STARTS
            setQe(qe, qa, ab, br, az, t)
            for (step in 0 until MSDFGEN_CUBIC_SEARCH_STEPS) {
                // Improve t
                d1.set(az).mul(t * t)
                    .add(br.x * 2f * t, br.y * 2f * t)
                    .add(ab.x, ab.y)
                    .mul(3f) // az * (3f * t * t) + br * (6f * t) + ab * 3f
                d2.set(az).mul(t).add(br).mul(6f) // az * (6f * t) + br * 6f
                t -= qe.dot(d1) / (d1.lengthSquared() + qe.dot(d2))
                if (t <= 0f || t >= 1f) break
                setQe(qe, qa, ab, br, az, t)
                val distance2 = qe.length()
                if (distance2 < abs(minDistance)) {
                    minDistance = nonZeroSign(crossProduct(direction(t, epDir), qe)) * distance2
                    param.value = t
                }
            }
        }

        dst.set(
            minDistance, when {
                param.value in 0f..1f -> 0f
                param.value < 0.5f -> absDotNormalized(direction(0f, epDir), qa)
                else -> absDotNormalizedXYY(direction(1f, epDir), p3, origin)
            }
        )

        JomlPools.vec2f.sub(8)

        return dst

    }

    private fun setQe(qe: Vector2f, qa: Vector2f, ab: Vector2f, br: Vector2f, az: Vector2f, t: Float) {
        // var qe = qa + (3 * t) * ab + (3 * t * t) * br + (t * t * t) * az
        // = qa + t*(3*ab + t * (3*br + t*az))
        val f0 = 3f * t
        val f1 = 3f * t * t
        val f2 = t * t * t
        qe.set(qa)
            .add(ab.x * f0, ab.y * f0)
            .add(br.x * f1, br.y * f1)
            .add(az.x * f2, az.y * f2)
    }

}