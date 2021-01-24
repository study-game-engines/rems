package me.anno.objects.animation

import me.anno.utils.Casting.castToDouble
import me.anno.utils.Casting.castToDouble2
import me.anno.utils.Casting.castToFloat
import me.anno.utils.Casting.castToFloat2
import me.anno.utils.Casting.castToInt
import me.anno.utils.Casting.castToInt2
import me.anno.utils.Casting.castToLong
import me.anno.utils.Casting.castToVector2f
import me.anno.utils.Casting.castToVector3f
import me.anno.utils.Casting.castToVector4d
import me.anno.utils.Casting.castToVector4f
import me.anno.utils.Maths.clamp
import org.joml.*
import kotlin.math.max

class Type(
    val defaultValue: Any,
    val components: Int,
    val unitScale: Float,
    val hasLinear: Boolean,
    val hasExponential: Boolean,
    val clampFunc: ((Any?) -> Any)?,
    val acceptOrNull: (Any) -> Any?
) {

    override fun toString() = "Type[${defaultValue.javaClass.simpleName} x $components]"

    fun <V> clamp(value: V): V = if (clampFunc != null) clampFunc.invoke(value) as V else value

    companion object {

        val ANY = Type(0, 16, 1f, true, true, null) { it }
        val INT = Type(0, 1, 1f, true, true, null, ::castToInt)
        val INT_PLUS = Type(0, 1, 1f, true, true, { max(castToInt2(it), 0) }, ::castToInt)
        val LONG = Type(0L, 1, 1f, true, true, null, ::castToLong)
        val FLOAT = Type(0f, 1, 1f, true, true, null, ::castToFloat)
        val FLOAT_01 = Type(0f, 1, 1f, true, true, { clamp(castToFloat2(it), 0f, 1f) }, ::castToFloat)
        val FLOAT_01_EXP = Type(0f, 1, 1f, false, true, { clamp(castToFloat2(it), 0f, 1f) }, ::castToFloat)
        val FLOAT_PLUS = Type(0f, 1, 1f, true, true, { max(castToFloat2(it), 0f) }, ::castToFloat)
        val FLOAT_PLUS_EXP = Type(0f, 1, 1f, false, true, { max(castToFloat2(it), 0f) }, ::castToFloat)
        val FLOAT_PERCENT = Type(100f, 1, 100f, true, false, { clamp(castToFloat2(it), 0f, 100f) }, ::castToFloat)
        val ANGLE = Type(0f, 1, 90f, true, false, null, ::castToFloat)
        val DOUBLE = Type(0.0, 1, 1f, true, true, null, ::castToDouble)
        val DOUBLE_PLUS = Type(0.0, 1, 1f, true, true, { max(castToDouble2(it), 0.0) }, ::castToDouble)
        val VEC2 = Type(Vector2f(), 2, 1f, true, true, null, ::castToVector2f)
        val VEC2_PLUS = Type(Vector2f(), 2, 1f, true, true, { max(castToFloat2(it), 0f) }, ::castToVector2f)
        val VEC3 = Type(Vector3f(), 3, 1f, true, true, null, ::castToVector3f)
        val VEC4 = Type(Vector4f(), 4, 1f, true, true, null, ::castToVector4f)
        val VEC4_PLUS = Type(Vector4f(), 4, 1f, true, true, {
            when (it) {
                is Float -> max(it, 0f)
                is Double -> max(it, 0.0)
                is Vector4f -> Vector4f(max(it.x, 0f), max(it.y, 0f), max(it.z, 0f), max(it.w, 0f))
                else -> throw RuntimeException("Unsupported type $it")
            }
        }, ::castToVector4f)
        val POSITION = Type(Vector3f(), 3, 1f, true, true, null, ::castToVector3f)
        val POSITION_2D = Type(Vector2f(), 2, 1f, true, true, null, ::castToVector2f)
        val SCALE = Type(Vector3f(1f, 1f, 1f), 3, 1f, true, true, null, ::castToVector3f)
        val ROT_YXZ = Type(Vector3f(), 3, 90f, true, true, null, ::castToVector3f)
        val ROT_XZ = Type(Vector3f(), 2, 90f, true, true, null, ::castToVector2f)
        val SKEW_2D = Type(Vector2f(), 2, 1f, true, true, null, ::castToVector2f)
        val QUATERNION = Type(Quaternionf(), 4, 1f, true, true, null) { if(it is Quaternionf) it else null }
        val COLOR = Type(Vector4f(1f, 1f, 1f, 1f), 4, 1f, true, true, {
            when (it) {
                is Vector4f -> {
                    it.x = clamp(it.x, 0f, 1f)
                    it.y = clamp(it.y, 0f, 1f)
                    it.z = clamp(it.z, 0f, 1f)
                    it.w = clamp(it.w, 0f, 1f)
                    it
                }
                is Float -> clamp(it, 0f, 1f)
                is Double -> clamp(it, 0.0, 1.0)
                else -> throw RuntimeException()
            }
        }, ::castToVector4f)
        val COLOR3 = Type(Vector3f(1f, 1f, 1f), 3, 1f, true, true, {
            when (it) {
                is Vector3f -> {
                    it.x = clamp(it.x, 0f, 1f)
                    it.y = clamp(it.y, 0f, 1f)
                    it.z = clamp(it.z, 0f, 1f)
                    it
                }
                is Float -> clamp(it, 0f, 1f)
                else -> throw RuntimeException()
            }
        }, ::castToVector3f)

        val TILING = Type(Vector4f(1f, 1f, 0f, 0f), 4, 1f, true, true, null, ::castToVector4f)

        /**
         * constant rate factor, 0 = lossless, 51 = worst, 23 = default
         * https://trac.ffmpeg.org/wiki/Encode/H.264
         * */
        val VIDEO_QUALITY_CRF = Type(23, 1, 1f, true, false, { clamp(it as Int, 0, 51) }, ::castToInt)

        val VEC4D = Type(Vector4d(), 4, 1f, true, true, null, ::castToVector4d)


    }

}