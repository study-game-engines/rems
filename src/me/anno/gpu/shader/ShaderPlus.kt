package me.anno.gpu.shader

import java.lang.RuntimeException

/**
 * a pair of a shader that is rendered normally,
 * and a shader, which renders a pure color for object click detection
 * */
class ShaderPlus(vertex: String, varying: String, fragment: String){

    // val correctShader = Shader(vertex, varying, fragment)
    // val monoShader = Shader(vertex, varying, makeMono(fragment))

    // universal
    val shader = Shader(vertex, varying, makeUniversal(fragment))

    fun makeUniversal(shader: String): String {

        val raw = shader.trim()
        if(!raw.endsWith("}")) throw RuntimeException()
        return "" +
                "uniform int drawMode;\n" +
                "" + raw.substring(0, raw.length-1) + "" +
                "   switch(drawMode){\n" +
                "       case ${DrawMode.ID.id}:\n" +
                "           if(gl_FragColor.a < 0.01) discard;\n" +
                "           gl_FragColor.rgb = (tint*tint).rgb;\n" +
                "           break;\n" +
                "       case ${DrawMode.DEPTH.id}:\n" +
                "           if(gl_FragColor.a < 0.01) discard;\n" +
                "           float depth = 0.5 + 0.04 * log2(zDistance);\n" +
                "           gl_FragColor.rgb = vec3(depth*depth);\n" +
                "           break;\n" +
                "   }" +
                "}"

    }

    fun makeMono(shader: String): String {
        val raw = shader.trim()
        if(!raw.endsWith("}")) throw RuntimeException()
        return raw.substring(0, raw.length-1) + "" +
                "   if(gl_FragColor.a < 0.01) discard;\n" +
                "   gl_FragColor.rgb = (tint*tint).rgb;\n" +
                // "   gl_FragColor.a = 0.01;// + 0.04 * log2(zDistance);\n" + does not work :(
                "}"
    }

    enum class DrawMode(val id: Int){
        COLOR(0),
        ID(1),
        DEPTH(2)
    }

}