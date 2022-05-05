package me.anno.gpu.deferred

import me.anno.Engine
import me.anno.gpu.GFX
import me.anno.gpu.GFX.flat01
import me.anno.gpu.OpenGL.useFrame
import me.anno.gpu.buffer.CubemapModel.cubemapModel
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.monitor.SubpixelLayout
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib.shader3D
import me.anno.gpu.shader.ShaderLib.simplestVertexShader
import me.anno.gpu.shader.ShaderLib.uvList
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.ITexture2D
import me.anno.image.ImageWriter
import me.anno.input.Input
import me.anno.input.Input.isControlDown
import me.anno.input.Input.isShiftDown
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.max
import me.anno.maths.Maths.min
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.mixARGB
import me.anno.ui.debug.TestDrawPanel.Companion.testDrawing
import org.apache.logging.log4j.LogManager
import org.joml.Matrix2f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11C.*
import kotlin.math.abs

/**
 * idea: use msaa on the depth in a separate pass,
 * and use its blurred information to find the correct amount of blurring,
 * which then can be applied to the colors to at least fix multi-pixel-wide edges
 *
 * in my simple test here, it works perfectly, but for curved planes, it didn't
 * -> use edge detection by walking along the edge, and find the interpolation values
 * */
object DepthBasedAntiAliasing {

    // not ideal, but still ok most times
    // todo curves are still an issue
    // if we are lucky, FSR 2.0 tells us how to implement this without bugs :)
    // only issue: axis aligned boxes...

    val shader = lazy {
        Shader(
            "Depth-Based FXAA",
            simplestVertexShader, uvList,
            "out vec4 fragColor;\n" +
                    // use color instead of depth?
                    // depth is great for false-positives, but bad for false-negatives (it blurs too few pixels)
                    // color does not guarantee the 2 wide transitions -> cannot really be used
                    "uniform sampler2D color,depth0;\n" +
                    "uniform bool showEdges,disableEffect;\n" +
                    "uniform float threshold;\n" +
                    "uniform vec2 rbOffset;\n" +
                    "bool needsBlur(ivec2 p){\n" +
                    "   float d0 = max(texelFetch(depth0,p,0).r,1e-38);\n" +
                    "   float d1 = texelFetch(depth0,p-ivec2(1,0),0).r;\n" +
                    "   float d2 = texelFetch(depth0,p-ivec2(0,1),0).r;\n" +
                    "   float d3 = texelFetch(depth0,p+ivec2(1,0),0).r;\n" +
                    "   float d4 = texelFetch(depth0,p+ivec2(0,1),0).r;\n" +
                    "   float sobel = 4.0-(d1+d2+d3+d4)/d0;\n" +
                    "   return abs(sobel) > threshold;\n" +
                    "}\n" +
                    "bool needsBlurX(ivec2 p){\n" +
                    "   float d0 = max(texelFetch(depth0,p,0).r,1e-38);\n" +
                    "   float d1 = texelFetch(depth0,p-ivec2(1,0),0).r;\n" +
                    "   float d2 = texelFetch(depth0,p+ivec2(1,0),0).r;\n" +
                    "   float sobel = 2.0-(d1+d2)/d0;\n" +
                    "   return abs(sobel) > threshold;\n" +
                    "}\n" +
                    "bool needsBlurY(ivec2 p){\n" +
                    "   float d0 = max(texelFetch(depth0,p,0).r,1e-38);\n" +
                    "   float d1 = texelFetch(depth0,p-ivec2(0,1),0).r;\n" +
                    "   float d2 = texelFetch(depth0,p+ivec2(0,1),0).r;\n" +
                    "   float sobel = 2.0-(d1+d2)/d0;\n" +
                    "   return abs(sobel) > threshold;\n" +
                    "}\n" +
                    "float smoothstep1(float x){\n" +
                    "   return pow(x,2.0)*(3.0-2.0*x);\n" +
                    "}\n" +
                    "float max(vec2 m){ return max(m.x,m.y); }\n" +
                    "float min(vec2 m){ return min(m.x,m.y); }\n" +
                    "void main(){\n" +
                    "   ivec2 p = ivec2(gl_FragCoord.xy);\n" +
                    "   ivec2 ts = textureSize(color, 0);\n" +
                    "   if(disableEffect || min(p) <= 0 || max(p-ts) >= -1){\n" +
                    "       fragColor = texelFetch(color, p, 0);\n" +
                    "       return;\n" +
                    "   }\n" +
                    "   if(showEdges){\n" +
                    "       fragColor = vec4(\n" +
                    "           needsBlur(p)  ? 1.0 : 0.0,\n" +
                    "           needsBlurX(p) ? 1.0 : 0.0,\n" +
                    "           needsBlurY(p) ? 1.0 : 0.0, 1.0);\n" +
                    "       return;\n" +
                    "   }\n" +
                    "   if(needsBlur(p)){\n" +
                    "#define maxSteps 15\n" +
                    "       float d0 = texelFetch(depth0,p,0).r;\n" +
                    "       float d1 = texelFetch(depth0,p-ivec2(1,0),0).r;\n" +
                    "       float d2 = texelFetch(depth0,p-ivec2(0,1),0).r;\n" +
                    "       float d3 = texelFetch(depth0,p+ivec2(1,0),0).r;\n" +
                    "       float d4 = texelFetch(depth0,p+ivec2(0,1),0).r;\n" +
                    "       float sobel = 4.0 - (d1+d2+d3+d4)/d0;\n" +
                    "       float dx = abs(d3-d1);\n" +
                    "       float dy = abs(d4-d2);\n" +
                    "       bool dirX = dx > dy;\n" + // dx > dy
                    "       if (min(dx, dy) * 1.05 > max(dx, dy)) {\n" +
                    // small corner: go to one of the edges, x/y doesn't matter
                    // not perfect, but pretty good
                    "           int ix = abs(d1-d0) > abs(d3-d0) ? +1 : -1;\n" +
                    "           float d02,d12,d22,d32,d42,dx2,dy2;\n" +
                    "           d02 = texelFetch(depth0,p+ivec2(ix,   0),0).r;\n" +
                    "           d12 = texelFetch(depth0,p+ivec2(ix-1, 0),0).r;\n" +
                    "           d22 = texelFetch(depth0,p+ivec2(ix,  -1),0).r;\n" +
                    "           d32 = texelFetch(depth0,p+ivec2(ix+1, 0),0).r;\n" +
                    "           d42 = texelFetch(depth0,p+ivec2(ix,  +1),0).r;\n" +
                    "           dx2 = abs(d32-d12);\n" +
                    "           dy2 = abs(d42-d22);\n" +
                    "           dirX = dx2 >= dy2;\n" +
                    "       }\n" +
                    "       int pos,neg;\n" +
                    "       ivec2 p2=p,dp=dirX?ivec2(0,1):ivec2(1,0);\n" +
                    "       for(pos=1;pos<=maxSteps;pos++){\n" +
                    "           p2+=dp;\n" +
                    "           if(!(dirX?needsBlurX(p2):needsBlurY(p2))) break;\n" +
                    "       }\n" +
                    "       p2=p;\n" +
                    "       for(neg=1;neg<=maxSteps;neg++){\n" +
                    "           p2-=dp;\n" +
                    "           if(!(dirX?needsBlurX(p2):needsBlurY(p2))) break;\n" +
                    "       }\n" +
                    "       float fraction = (float(pos)-0.5)/float(pos+neg-1);\n" +
                    "       float blur = neg==maxSteps && pos==maxSteps ? 0.0 : min(1.0-fraction,fraction);\n" +
                    "       blur = smoothstep1(blur);\n" + // sharpen the edge from 2 wide to 1 wide
                    "       int other = (dirX?" +
                    "           abs(d1-d0) > abs(d3-d0) : " +
                    "           abs(d2-d0) > abs(d4-d0)) ? -1 : +1;\n" +
                    "       vec4 mixColor = texelFetch(color,p+(dirX?ivec2(other,0):ivec2(0,other)),0);\n" +
                    "       vec4 baseColor = texelFetch(color,p,0);\n" +
                    "       vec2 offset = rbOffset * float(other);\n" +
                    "       if(dirX){\n" +
                    "           vec2 ga = mix(baseColor.ga, mixColor.ga, blur);\n" +
                    "           float r = mix(baseColor.r,  mixColor.r,  max(blur-offset.x, 0.0));\n" +
                    "           float b = mix(baseColor.b,  mixColor.b,  max(blur+offset.x, 0.0));\n" +
                    "           fragColor = vec4(r,ga.x,b,ga.y);\n" +
                    "       } else {\n" +
                    "           vec2 ga = mix(baseColor.ga, mixColor.ga, blur);\n" +
                    "           float r = mix(baseColor.r,  mixColor.r,  max(blur-offset.y, 0.0));\n" +
                    "           float b = mix(baseColor.b,  mixColor.b,  max(blur+offset.y, 0.0));\n" +
                    "           fragColor = vec4(r,ga.x,b,ga.y);\n" +
                    "       }\n" +
                    "   } else {\n" +
                    "       fragColor = texelFetch(color, p, 0);\n" +
                    "   }\n" +
                    "}"
        ).apply { setTextureIndices(listOf("color", "depth0")) }
    }

    fun render(color: ITexture2D, depth: ITexture2D, threshold: Float = 1e-5f) {
        val enableDebugControls = false
        val shader = shader.value
        shader.use()
        shader.v1f("threshold", threshold)
        shader.v1b("disableEffect", enableDebugControls && isControlDown)
        // use the subpixel layout to define the correct subpixel rendering
        // only works for RGB or BGR, otherwise just will cancel to 0
        val sr = SubpixelLayout.r
        val sb = SubpixelLayout.b
        // 0.5 for mean, 0.5 to make the effect less obvious
        shader.v2f("rbOffset", (sr.x - sb.x) * 0.25f, (sr.y - sb.y) * 0.25f)
        shader.v1b("showEdges", enableDebugControls && isShiftDown)
        depth.bindTrulyNearest(1)
        color.bindTrulyNearest(0)
        flat01.draw(shader)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        testShader()
        // testEdgeAA()
    }

    private fun testShader() {
        val transform = Matrix4f()
        var angle = 0f
        testDrawing {
            val w = it.w / 8
            val h = it.h / 8
            transform.identity()
            transform.perspective(1f, w.toFloat() / h, 0.01f, 100f)
            transform.translate(0f, 0f, -5f)
            val delta = 0.2f * Engine.deltaTime
            if (Input.isLeftDown) angle += delta
            if (Input.isRightDown) angle -= delta
            transform.rotateZ(angle)
            val depth = FBStack["depth", w, h, 4, false, 1, false]
            val mesh = cubemapModel
            mesh.ensureBuffer()
            useFrame(depth) {
                glClearColor(1f, 0.7f, 0f, 1f)
                glClear(GL_COLOR_BUFFER_BIT)
                val shader = shader3D.value
                shader.use()
                shader.m4x4("transform", transform)
                mesh.draw(shader)
            }
            val result = FBStack["result", w, h, 4, false, 1, false]
            useFrame(result) {
                val shader = shader.value
                shader.use()
                shader.v1b("showEdges", isShiftDown)
                shader.v1b("disableEffect", isControlDown)
                shader.v1f("threshold", 1e-5f)
                shader.v2f("rbOffset", 0f, 0f) // red-blue-offset; disabled for testing
                depth.bindTexture0(0, GPUFiltering.NEAREST, Clamping.CLAMP)
                depth.bindTexture0(1, GPUFiltering.NEAREST, Clamping.CLAMP)
                flat01.draw(shader)
            }
            GFX.copy(result)
        }
    }

    private fun testEdgeAA() {

        val logger = LogManager.getLogger(DepthBasedAntiAliasing::class)

        // task: to find the correct formula, maybe draw a graph of fraction ~ correct blur
        // result: there is no simple correct formula, it seems... there must be something inheritely wrong...

        val size = 128
        val sm1 = size - 1

        fun sample(f: IntArray, x: Int, y: Int): Int {
            return f[clamp(x, 0, sm1) + clamp(y, 0, sm1) * size]
        }

        fun sample(f: FloatArray, x: Int, y: Int): Float {
            return f[clamp(x, 0, sm1) + clamp(y, 0, sm1) * size]
        }

        val rot = Matrix2f()
            .rotate(0.5f)

        val planes = Array(4) {
            val dx = it.and(1) * 2 - 1f
            val dy = it.and(2) - 1f
            val normal = rot.transform(Vector2f(dx, dy))
            val position = Vector2f(dx, dy).mul(-size / 4f).add(size / 2f, size / 2f)
            Vector3f(normal, -normal.dot(position))
        }

        fun render(dst: FloatArray, v0: Float = 0f, v1: Float = 1f) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val xf = x + 0.5f
                    val yf = y + 0.5f
                    dst[x * size + y] = if (planes.all { it.dot(xf, yf, 1f) >= 0f }) v1 else v0
                }
            }
        }

        fun renderHQ(dst: FloatArray, q: Int = 20, v0: Float = 0f, v1: Float = 1f) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    var sum = 0f
                    for (fx in 0 until q) {
                        for (fy in 0 until q) {
                            val xf = x + fx.toFloat() / q
                            val yf = y + fy.toFloat() / q
                            sum += if (planes.all { it.dot(xf, yf, 1f) >= 0f }) v1 else v0
                        }
                    }
                    dst[x * size + y] = sum / (q * q)
                }
            }
        }

        val f = FloatArray(size * size)
        render(f)

        ImageWriter.writeRGBImageInt(size * 4, size * 4, "raw.png", -1) { x, y, _ ->
            (sample(f, x / 4, y / 4) * 255).toInt() * 0x10101
        }

        /* val q = FloatArray(size * size)
         renderHQ(q, 20)
         ImageWriter.writeRGBImageInt(size * 4, size * 4, "hq.png", -1) { x, y, _ ->
             (sample(q, x / 4, y / 4) * 255).toInt() * 0x10101
         }*/

        // val points = ArrayList<Vector2f>()

        val r = IntArray(size * size)
        val threshold = 0.1f
        val c0 = 0x337733
        val c1 = 0x33ff33
        for (y in 0 until size) {
            for (x in 0 until size) {
                val i = x + y * size
                fun needsBlur(dx: Int, dy: Int): Boolean {
                    val rel = (sample(f, x + dx + 1, y + dy) +
                            sample(f, x + dx, y + dy + 1) +
                            sample(f, x + dx - 1, y + dy) +
                            sample(f, x + dx, y + dy - 1)
                            ) / sample(f, x + dx, y + dy)
                    return abs(4f - rel) > threshold
                }

                val baseColor = if (sample(f, x, y) > 0f) c0 else c1
                if (needsBlur(0, 0)) {
                    var d0 = sample(f, x, y)
                    var d1 = sample(f, x + 1, y)
                    var d2 = sample(f, x, y + 1)
                    var d3 = sample(f, x - 1, y)
                    var d4 = sample(f, x, y - 1)
                    var dx = abs(d3 - d1)
                    var dy = abs(d4 - d2)
                    var dirX = dx >= dy
                    if (min(dx, dy) * 1.1f > max(dx, dy)) {
                        // small corner: go to one of the edges, x/y doesn't matter
                        val ix = if (abs(d1 - d0) > abs(d3 - d0)) -1 else +1
                        d0 = sample(f, x + ix, y)
                        d1 = sample(f, x + ix + 1, y)
                        d2 = sample(f, x + ix, y + 1)
                        d3 = sample(f, x + ix - 1, y)
                        d4 = sample(f, x + ix, y - 1)
                        dx = abs(d3 - d1)
                        dy = abs(d4 - d2)
                        dirX = dx >= dy
                    }
                    val stepX = if (dirX) 0 else 1
                    val stepY = if (dirX) 1 else 0
                    var pos = 1
                    var neg = 1
                    while (pos < 15) {
                        if (!needsBlur(pos * stepX, pos * stepY)) break
                        pos++
                    }
                    while (neg < 15) {
                        if (!needsBlur(-neg * stepX, -neg * stepY)) break
                        neg++
                    }
                    pos--
                    neg--
                    val fraction = (pos + 0.5f) / (1f + pos + neg)
                    var blur = min(fraction, 1f - fraction)
                    blur = blur * blur * (3 - 2 * blur) + 0f
                    // val blur = min(1f, 2 * abs(fraction - 0.5f))
                    val other =
                        if (dirX) abs(d1 - d0) >= abs(d3 - d0)
                        else abs(d2 - d0) >= abs(d4 - d0)
                    val other2 = if (other) +1 else -1
                    val otherColor = if (sample(
                            f,
                            x + if (dirX) other2 else 0,
                            y + if (dirX) 0 else other2
                        ) > 0f
                    ) c0 else c1
                    if (y > size / 2 && otherColor == baseColor) {
                        logger.warn("Incorrect border color! $x $y")
                        r[i] = mix(0xff0000, baseColor, 0.5f)
                    } else {
                        r[i] = when (x * 3 / size) {
                            0 -> (fraction * 255).toInt() * 0x10101
                            1 -> mixARGB(baseColor, otherColor, blur)
                            else -> (blur * 255).toInt() * 0x10101
                        }
                        /*val correctFraction = if (baseColor == c0) q[i] else 1f - q[i]
                        points.add(Vector2f(blur, correctFraction))*/
                        // r[i] = mix(0x777777, baseColor, 0.5f) // mixARGB(baseColor, otherColor, blur)
                    }
                } else {
                    r[i] = baseColor
                }

            }
        }

        ImageWriter.writeRGBImageInt(size * 4, size * 4, "aa.png", -1) { x, y, _ ->
            sample(r, x / 4, y / 4)
        }

        /*(points.map { it.x }.joinToString("\n"))
        ("----------------------------------------------------------")
        (points.map { it.y }.joinToString("\n"))*/

    }

}