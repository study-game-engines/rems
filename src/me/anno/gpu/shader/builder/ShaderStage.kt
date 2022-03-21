package me.anno.gpu.shader.builder

class ShaderStage(
    val callName: String,
    val variables: List<Variable>,
    val body: String
) {

    val attributes = variables.filter { it.inOutMode == VariableMode.ATTR }

    val functions = ArrayList<Function>()

    val defines = ArrayList<String>()

    /* constructor(name: String, parameters: List<Variable>, body: String) : this(name, parameters) {
         functions += Function(name, parameters, body)
     }*/

    fun define(value: String): ShaderStage {
        defines += value
        return this
    }

}