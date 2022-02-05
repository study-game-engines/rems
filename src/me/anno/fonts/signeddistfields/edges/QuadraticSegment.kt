package me.anno.fonts.signeddistfields.edges

import me.anno.fonts.signeddistfields.algorithm.EquationSolver.solveCubic
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.absDotNormalized
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.absDotNormalizedXYY
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.crossProduct
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.crossProductXYY
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.dotProduct
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.dotProductXXY
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.nonZeroSign
import me.anno.fonts.signeddistfields.algorithm.SDFMaths.union
import me.anno.fonts.signeddistfields.structs.FloatPtr
import me.anno.fonts.signeddistfields.structs.SignedDistance
import me.anno.maths.Maths.sq
import me.anno.utils.pooling.JomlPools
import me.anno.utils.types.Vectors.minus
import me.anno.utils.types.Vectors.plus
import me.anno.utils.types.Vectors.times
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector2fc
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class QuadraticSegment(val p0: Vector2fc, p10: Vector2fc, val p2: Vector2fc) : EdgeSegment() {

    val p1 = if (p0 == p10 || p10 == p2) (p0 + p2) * 0.5f else p10

    override fun toString() = "[$p0 $p1 $p2]"

    override fun point(param: Float, dst: Vector2f): Vector2f {
        val f0 = sq(1f - param)
        val f1 = 2f * (1f - param) * param
        val f2 = param * param
        return dst.set(p0).mul(f0)
            .add(p1.x() * f1, p1.y() * f1)
            .add(p2.x() * f2, p2.y() * f2)
    }

    override fun direction(param: Float, dst: Vector2f): Vector2f {
        val b = 1f - param
        val b2 = b * b
        val a2 = param * param
        val ba = b - param
        dst.set(p0).mul(-b2)
            .add(p1.x() * ba, p1.y() * ba)
            .add(p2.x() * a2, p2.y() * a2)
        if (dst.length() == 0f) return dst.set(p2).sub(p0)
        return dst
    }

    override fun length(): Float {
        val ab: Vector2f = p1 - p0
        val br: Vector2f = p2 - p1 - ab
        val abab = p0.distanceSquared(p1)
        val abbr = ab.dot(br)
        val brbr = br.lengthSquared()
        val abLen = sqrt(abab)
        val brLen = sqrt(brbr)
        val crs = crossProduct(ab, br)
        val h = sqrt(abab + abbr + abbr + brbr)
        return (brLen * ((abbr + brbr) * h - abbr * abLen) +
                crs * crs * ln((brLen * h + abbr + brbr) / (brLen * abLen + abbr))) / (brbr * brLen)
    }

    override fun union(bounds: AABBf, tmp: FloatArray) {

        union(bounds, p0)
        union(bounds, p1)

        val bot = JomlPools.vec2f.create()
            .set(p1).add(p1)
            .sub(p0).sub(p2)

        if (bot.x != 0f) {
            val param = (p1.x() - p0.x()) / bot.x
            if (param > 0 && param < 1) union(bounds, point(param, bot))
        } else {
            val param = (p1.y() - p0.y()) / bot.y
            if (param > 0 && param < 1) union(bounds, point(param, bot))
        }

        JomlPools.vec2f.sub(1)

    }

    override fun signedDistance(origin: Vector2fc, param: FloatPtr, dst: SignedDistance): SignedDistance {

        val qa = JomlPools.vec2f.create()
        val ab = JomlPools.vec2f.create()
        val br = JomlPools.vec2f.create()

        qa.set(p0).sub(origin)
        ab.set(p1).sub(p0)
        br.set(p2).sub(p1).sub(ab)

        val a = dotProduct(br, br)
        val b = 3 * dotProduct(ab, br)
        val c = 2 * dotProduct(ab, ab) + dotProduct(qa, br)
        val d = dotProduct(qa, ab)
        val t = FloatArray(3)
        val solutions = solveCubic(t, a, b, c, d)

        val epDir = JomlPools.vec2f.create()

        direction(0f, epDir)
        var minDistance = nonZeroSign(crossProduct(epDir, qa)) * qa.length() // distance from A
        param.value = -dotProduct(qa, epDir) / dotProduct(epDir, epDir)

        direction(1f, epDir)
        val distance = p2.distance(origin) // distance from B
        if (distance < abs(minDistance)) {
            val cross = crossProductXYY(epDir, p2, origin)
            minDistance = if (cross >= 0f) +distance else -distance
            param.value = dotProductXXY(origin, p1, epDir) / dotProduct(epDir, epDir)
        }

        for (i in 0 until solutions) {
            if (t[i] > 0 && t[i] < 1) {
                val qe = p0 + ab * (2 * t[i]) + br * (t[i] * t[i]) - origin
                val distance2 = qe.length()
                if (distance2 <= abs(minDistance)) {
                    val cross = crossProduct(direction(t[i], Vector2f()), qe)
                    minDistance = if (cross >= 0f) distance2 else -distance2
                    param.value = t[i]
                }
            }
        }

        dst.set(
            minDistance, when {
                param.value in 0f..1f -> 0f
                param.value < .5f -> absDotNormalized(direction(0f, epDir), qa)
                else -> absDotNormalizedXYY(direction(1f, epDir), p2, origin)
            }
        )

        JomlPools.vec2f.sub(4)

        return dst
    }


}