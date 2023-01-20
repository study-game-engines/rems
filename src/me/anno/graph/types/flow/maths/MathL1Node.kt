package me.anno.graph.types.flow.maths

import me.anno.graph.EnumNode
import me.anno.graph.types.FlowGraph
import me.anno.graph.types.flow.ValueNode
import me.anno.graph.ui.GraphEditor
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.ui.base.groups.PanelList
import me.anno.ui.input.EnumInput
import me.anno.ui.style.Style

class MathL1Node() : ValueNode("Integer Math 1", inputs, outputs), EnumNode {

    enum class IntMathsUnary(
        val id: Int,
        val glsl: String,
        val int: (a: Int) -> Int,
        val long: (a: Long) -> Long
    ) {

        ABS(0, "abs(a)", { a -> kotlin.math.abs(a) }, { a -> kotlin.math.abs(a) }),
        NEG(1, "-a", { a -> -a }, { a -> -a }),
        NOT(2, "~a", { a -> a.inv() }, { a -> a.inv() }), // = -x-1
        SQRT(
            3, "sqrt(a)",
            { a -> kotlin.math.sqrt(a.toDouble()).toInt() },
            { a -> kotlin.math.sqrt(a.toDouble()).toLong() }),

        ;

        companion object {
            val values = values()
            val byId = values.associateBy { it.id }
        }

    }

    constructor(type: IntMathsUnary) : this() {
        this.type = type
    }

    override fun listNodes() = IntMathsUnary.values.map { MathL1Node(it) }

    var type: IntMathsUnary = IntMathsUnary.ABS
        set(value) {
            field = value
            name = "Int " + value.name
        }

    override fun createUI(g: GraphEditor, list: PanelList, style: Style) {
        super.createUI(g, list, style)
        list += EnumInput("Type", true, type.name, IntMathsUnary.values.map { NameDesc(it.name, it.glsl, "") }, style)
            .setChangeListener { _, index, _ ->
                type = IntMathsUnary.values[index]
                g.onChange(false)
            }
    }

    override fun compute(graph: FlowGraph) {
        val inputs = inputs!!
        val a = graph.getValue(inputs[0]) as Long
        setOutput(type.long(a), 0)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeEnum("type", type)
    }

    override fun readInt(name: String, value: Int) {
        if (name == "type") type = IntMathsUnary.byId[value] ?: type
        else super.readInt(name, value)
    }

    override val className get() = "MathL1Node"

    companion object {
        val inputs = listOf("Long", "A")
        val outputs = listOf("Long", "Result")
    }

}