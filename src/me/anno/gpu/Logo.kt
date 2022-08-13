package me.anno.gpu

import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.GFXState.renderPurely
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.io.ISaveable.Companion.registerCustomClass
import me.anno.io.ResourceHelper
import me.anno.io.files.InvalidRef
import me.anno.io.zip.InnerPrefabFile
import me.anno.mesh.obj.OBJReader
import me.anno.utils.Color.b01
import me.anno.utils.Color.g01
import me.anno.utils.Color.r01
import me.anno.utils.Color.toVecRGB
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

// can be set by the application
var logoBackgroundColor = 0
var logoIconColor = 0x212256

val shader by lazy {

    val shader = Shader(
        "logo", listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.V3F, "size")
        ), "void main(){ gl_Position = vec4(coords * size, 1.0); }",
        emptyList(), emptyList(), "" +
                "void main(){\n" + // color uniform didn't want to work :/, why?
                "   gl_FragColor = vec4(${logoIconColor.toVecRGB().run { "$x, $y, $z" }}, 1.0);\n" +
                "}"
    )

    shader.use()
    shader.ignoreNameWarnings("normals", "uvs", "tangents", "colors")

    shader

}

val frame by lazy {
    Framebuffer(
        "logo", 1, 1, min(8, GFX.maxSamples),
        1, false, DepthBufferType.NONE
    )
}

fun drawLogo(window: WindowX, destroy: Boolean) {

    GFX.check()

    val logger = LogManager.getLogger("Logo")
    logger.info("Showing Engine Logo")

    // extend space left+right/top+bottom (zooming out a little)
    val width = window.width
    val height = window.height
    val sw: Float
    val sh: Float
    if (width > height) {
        sw = height.toFloat() / width
        sh = 1f
    } else {
        sw = 1f
        sh = width.toFloat() / height
    }

    val shader = shader
    shader.use()
    shader.v3f("size", sw, sh, 0f)

    GFX.check()

    GFX.maxSamples = max(1, glGetInteger(GL30C.GL_MAX_SAMPLES))
    val frame = if (GFX.maxSamples > 1) frame else null

    if (frame != null) {
        GFX.setFrameNullSize(window)
        useFrame(width, height, true, frame) {
            drawLogo(shader)
        }
        GFX.copy(frame)
    } else drawLogo(shader)

    if (destroy) {
        frame?.destroy()
        shader.destroy()
    }

    GFX.check()

}

fun drawLogo(shader: Shader) {

    // load icon.obj as file, and draw it
    val c = logoBackgroundColor
    GFXState.currentBuffer.clearColor(c.r01(), c.g01(), c.b01(), 1f)

    renderPurely {
        try {
            // ensure mesh is a known class
            registerCustomClass(Mesh())
            // you can override this file, if you want to change the logo
            // but please show Rem's Engine somewhere in there!
            val stream = ResourceHelper.loadResource("icon.obj")
            val reader = OBJReader(stream, InvalidRef)
            val file = reader.meshesFolder
            for (child in file.listChildren()) {

                // we could use the name as color... probably a nice idea :)
                val prefab = (child as InnerPrefabFile).prefab
                val mesh = prefab.getSampleInstance() as? Mesh ?: continue

                mesh.ensureBuffer()
                for (i in 0 until mesh.numMaterials) {
                    mesh.draw(shader, i)
                }
                mesh.destroy()

            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}