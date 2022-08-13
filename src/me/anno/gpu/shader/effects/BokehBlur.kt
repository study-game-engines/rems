package me.anno.gpu.shader.effects

import me.anno.gpu.GFX.flat01
import me.anno.gpu.GFXState.renderPurely
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.Frame
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Renderer
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib.createShader
import me.anno.gpu.shader.ShaderLib.simplestVertexShader
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.Texture2D
import me.anno.maths.Maths.clamp
import org.joml.Vector4f
import org.lwjgl.opengl.GL11C.*
import kotlin.math.roundToInt

/**
 * shader by Kleber Garcia, 'Kecho', 2017, MIT license (https://github.com/kecho/CircularDofFilterGenerator)
 * the shader was modified to work without ShaderToy, and the filter texture was uploaded directly
 * done more steps for larger sizes of bokeh blur
 * */
object BokehBlur {

    private const val KERNEL_RADIUS = 8
    private const val KERNEL_COUNT = KERNEL_RADIUS * 2 + 1

    private var compositionShader: BaseShader? = null
    private var perChannelShader: BaseShader? = null

    private val filterTexture = Texture2D("bokeh", KERNEL_COUNT, 1, 1)

    fun draw(srcTexture: Texture2D, target: Framebuffer, relativeToH: Float, fp: Boolean) {

        val w = srcTexture.w
        val h = srcTexture.h

        if (compositionShader == null) init()

        renderPurely {

            val r = FBStack["bokeh-r", w, h, 4, fp, 1, false]
            val g = FBStack["bokeh-g", w, h, 4, fp, 1, false]
            val b = FBStack["bokeh-b", w, h, 4, fp, 1, false]
            val a = FBStack["bokeh-a", w, h, 4, fp, 1, false]

            val pixelRadius = relativeToH * h
            val normRadius = pixelRadius / KERNEL_RADIUS

            //val stepsRadius = max(KERNEL_RADIUS, pixelRadius.roundToInt())
            //val step = pixelRadius/stepsRadius

            filterTexture.bind(0, GPUFiltering.LINEAR, Clamping.CLAMP)
            srcTexture.bind(1, GPUFiltering.LINEAR, Clamping.CLAMP)

            drawX(normRadius, w, h, r, g, b, a)
            drawY(normRadius, w, h, r, g, b, a, target)

        }

    }

    fun drawX(
        normRadius: Float, w: Int, h: Int,
        r: Framebuffer, g: Framebuffer, b: Framebuffer, a: Framebuffer
    ) {
        val shader = perChannelShader!!.value
        shader.use()
        uniforms(shader, w, h, normRadius)
        drawChannel(shader, r, w, h, xAxis)
        drawChannel(shader, g, w, h, yAxis)
        drawChannel(shader, b, w, h, zAxis)
        drawChannel(shader, a, w, h, wAxis)
    }

    private val xAxis = Vector4f(1f, 0f, 0f, 0f)
    private val yAxis = Vector4f(0f, 1f, 0f, 0f)
    private val zAxis = Vector4f(0f, 0f, 1f, 0f)
    private val wAxis = Vector4f(0f, 0f, 0f, 1f)

    private fun uniforms(shader: Shader, w: Int, h: Int, normRadius: Float) {
        val radius = normRadius * KERNEL_RADIUS
        shader.v2f("stepVal", radius / w, radius / h)
        val radiusI = clamp(radius.roundToInt(), KERNEL_RADIUS, 64)
        shader.v1i("radius", radiusI)
        shader.v1f("multiplier", KERNEL_RADIUS.toFloat() / radiusI)
    }

    fun drawY(
        normRadius: Float, w: Int, h: Int,
        r: Framebuffer, g: Framebuffer, b: Framebuffer, a: Framebuffer,
        target: Framebuffer
    ) {

        useFrame(w, h, true, target, Renderer.copyRenderer) {

            val shader = compositionShader!!.value
            shader.use()
            uniforms(shader, w, h, normRadius)

            target.clearColor(0)

            // filter texture is bound correctly
            r.bindTexture0(1, GPUFiltering.LINEAR, Clamping.CLAMP)
            g.bindTexture0(2, GPUFiltering.LINEAR, Clamping.CLAMP)
            b.bindTexture0(3, GPUFiltering.LINEAR, Clamping.CLAMP)
            a.bindTexture0(4, GPUFiltering.LINEAR, Clamping.CLAMP)
            flat01.draw(shader)

        }

    }

    fun drawChannel(shader: Shader, target: Framebuffer, w: Int, h: Int, channel: Vector4f) {
        useFrame(w, h, true, target, Renderer.copyRenderer) {
            Frame.bind()
            shader.v4f("channelSelection", channel)
            flat01.draw(shader)
        }
    }

    fun init() {

        val loopUniforms = "" +
                "uniform int radius;\n" +
                "uniform float multiplier;\n"

        val loop = "" +
                "for (int i=-radius;i<=radius;i++){\n" +
                "   float f11 = float(i)/float(radius);\n" + // -1 .. +1
                "   float f01 = f11*0.5-0.5;\n" // 0 .. 1

        val vertexShader = simplestVertexShader

        val getFilters = "" +
                "vec4 getFilters(float x){\n" +
                "   return texture(filterTexture, vec2(x, 0));\n" +
                "}\n"

        val varyingShader = listOf(Variable(GLSLType.V2F, "uv"))

        perChannelShader = createShader(
            "bokeh-perChannel", vertexShader, varyingShader, "" +

                    "uniform vec2 stepVal;\n" + // 1/resolution
                    "uniform sampler2D image, filterTexture;\n" +
                    "uniform vec4 channelSelection;\n" +

                    getFilters +
                    loopUniforms +

                    "void main(){\n" +
                    "   vec4 sum = vec4(0);\n" +
                    loop +
                    "       vec2 coords = uv + vec2(stepVal.x*f11,0.0);\n" +
                    "       float imageTexelR = dot(texture(image, coords), channelSelection);\n" +
                    "       vec4 c0_c1 = getFilters(f01);\n" +
                    "       sum += imageTexelR * c0_c1;\n" +
                    "    }\n" +
                    "    gl_FragColor = sum * multiplier;\n" +
                    "}", listOf("filterTexture", "image")
        )

        compositionShader = createShader(
            "bokeh-composition", vertexShader, varyingShader, "" +

                    "uniform vec2 stepVal;\n" + // 1/resolution
                    "uniform sampler2D inputRed, inputGreen, inputBlue, inputAlpha, filterTexture;\n" +
                    "const vec2 Kernel0Weights_RealX_ImY = vec2(0.411259,-0.548794);\n" +
                    "const vec2 Kernel1Weights_RealX_ImY = vec2(0.513282, 4.561110);\n" +

                    "vec2 mulComplex(vec2 p, vec2 q){\n" +
                    "    return vec2(p.x*q.x-p.y*q.y, p.x*q.y+p.y*q.x);\n" +
                    "}\n" +

                    getFilters +
                    loopUniforms +

                    "void main(){\n" +

                    "   vec4 valR = vec4(0);\n" +
                    "   vec4 valG = vec4(0);\n" +
                    "   vec4 valB = vec4(0);\n" +
                    "   vec4 valA = vec4(0);\n" +

                    loop +

                    "       vec2 coords = uv + vec2(0.0,stepVal.y*f11);\n" +
                    "       vec4 imageTexelR = texture(inputRed,   coords);  \n" +
                    "       vec4 imageTexelG = texture(inputGreen, coords); \n" +
                    "       vec4 imageTexelB = texture(inputBlue,  coords);  \n" +
                    "       vec4 imageTexelA = texture(inputAlpha, coords);  \n" +

                    "       vec4 c0_c1 = getFilters(f01);\n" +

                    "       valR.xy += mulComplex(imageTexelR.xy, c0_c1.xy);\n" +
                    "       valR.zw += mulComplex(imageTexelR.zw, c0_c1.zw);\n" +

                    "       valG.xy += mulComplex(imageTexelG.xy, c0_c1.xy);\n" +
                    "       valG.zw += mulComplex(imageTexelG.zw, c0_c1.zw);\n" +

                    "       valB.xy += mulComplex(imageTexelB.xy, c0_c1.xy);\n" +
                    "       valB.zw += mulComplex(imageTexelB.zw, c0_c1.zw);\n" +

                    "       valA.xy += mulComplex(imageTexelA.xy, c0_c1.xy);\n" +
                    "       valA.zw += mulComplex(imageTexelA.zw, c0_c1.zw);\n" +

                    "   }\n" +

                    "   valR *= multiplier;\n" +
                    "   valG *= multiplier;\n" +
                    "   valB *= multiplier;\n" +
                    "   valA *= multiplier;\n" +

                    "   float rChannel = dot(valR, vec4(Kernel0Weights_RealX_ImY, Kernel1Weights_RealX_ImY));\n" +
                    "   float gChannel = dot(valG, vec4(Kernel0Weights_RealX_ImY, Kernel1Weights_RealX_ImY));\n" +
                    "   float bChannel = dot(valB, vec4(Kernel0Weights_RealX_ImY, Kernel1Weights_RealX_ImY));\n" +
                    "   float aChannel = dot(valA, vec4(Kernel0Weights_RealX_ImY, Kernel1Weights_RealX_ImY));\n" +
                    "   gl_FragColor = vec4(rChannel, gChannel, bChannel, aChannel);\n" +

                    "}", listOf("filterTexture", "inputRed", "inputGreen", "inputBlue", "inputAlpha")
        )

        val kernel0 = floatArrayOf(
            0.014096f, -0.022658f, 0.055991f, 0.004413f,
            -0.020612f, -0.025574f, 0.019188f, 0.000000f,
            -0.038708f, 0.006957f, 0.000000f, 0.049223f,
            -0.021449f, 0.040468f, 0.018301f, 0.099929f,
            0.013015f, 0.050223f, 0.054845f, 0.114689f,
            0.042178f, 0.038585f, 0.085769f, 0.097080f,
            0.057972f, 0.019812f, 0.102517f, 0.068674f,
            0.063647f, 0.005252f, 0.108535f, 0.046643f,
            0.064754f, 0.000000f, 0.109709f, 0.038697f,
            0.063647f, 0.005252f, 0.108535f, 0.046643f,
            0.057972f, 0.019812f, 0.102517f, 0.068674f,
            0.042178f, 0.038585f, 0.085769f, 0.097080f,
            0.013015f, 0.050223f, 0.054845f, 0.114689f,
            -0.021449f, 0.040468f, 0.018301f, 0.099929f,
            -0.038708f, 0.006957f, 0.000000f, 0.049223f,
            -0.020612f, -0.025574f, 0.019188f, 0.000000f,
            0.014096f, -0.022658f, 0.055991f, 0.004413f
        )
        val kernel1 = floatArrayOf(
            0.000115f, 0.009116f, 0.000000f, 0.051147f,
            0.005324f, 0.013416f, 0.009311f, 0.075276f,
            0.013753f, 0.016519f, 0.024376f, 0.092685f,
            0.024700f, 0.017215f, 0.043940f, 0.096591f,
            0.036693f, 0.015064f, 0.065375f, 0.084521f,
            0.047976f, 0.010684f, 0.085539f, 0.059948f,
            0.057015f, 0.005570f, 0.101695f, 0.031254f,
            0.062782f, 0.001529f, 0.112002f, 0.008578f,
            0.064754f, 0.000000f, 0.115526f, 0.000000f,
            0.062782f, 0.001529f, 0.112002f, 0.008578f,
            0.057015f, 0.005570f, 0.101695f, 0.031254f,
            0.047976f, 0.010684f, 0.085539f, 0.059948f,
            0.036693f, 0.015064f, 0.065375f, 0.084521f,
            0.024700f, 0.017215f, 0.043940f, 0.096591f,
            0.013753f, 0.016519f, 0.024376f, 0.092685f,
            0.005324f, 0.013416f, 0.009311f, 0.075276f,
            0.000115f, 0.009116f, 0.000000f, 0.051147f
        )

        val kernelTexture = FloatArray(KERNEL_COUNT * 4)
        for (i in 0 until 4 * KERNEL_COUNT step 4) {
            kernelTexture[i] = kernel0[i]
            kernelTexture[i + 1] = kernel0[i + 1]
            kernelTexture[i + 2] = kernel1[i]
            kernelTexture[i + 3] = kernel1[i + 1]
        }

        filterTexture.createRGBA(kernelTexture, false)

    }

    fun destroy() {
        filterTexture.destroy()
        compositionShader?.destroy()
        perChannelShader?.destroy()
    }

}