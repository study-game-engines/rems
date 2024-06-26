package me.anno.graph.types.flow.maths

import me.anno.graph.EnumNode
import me.anno.graph.types.flow.FlowGraphNodeUtils.getIntInput

val dataI1 = MathNode.MathNodeData(
    IntMathsUnary.entries,
    listOf("Int"), "Int",
    { it.id }, { it.glsl }
)

class MathI1Node : MathNode<IntMathsUnary>(dataI1), EnumNode, GLSLFuncNode {
    override fun compute() {
        setOutput(0, type.int(getIntInput(0)))
    }
}

val dataI2 = MathNode.MathNodeData(
    IntMathsBinary.entries,
    listOf("Int", "Int"), "Int",
    { it.id }, { it.glsl }
)

class MathI2Node : MathNode<IntMathsBinary>(dataI2), EnumNode, GLSLFuncNode {
    override fun compute() {
        setOutput(0, type.int(getIntInput(0), getIntInput(1)))
    }
}

val dataI3 = MathNode.MathNodeData(
    IntMathsTernary.entries,
    listOf("Int", "Int", "Int"), "Int",
    { it.id }, { it.glsl }
)

class MathI3Node : MathNode<IntMathsTernary>(dataI3), EnumNode, GLSLFuncNode {
    override fun compute() {
        setOutput(0, type.int(getIntInput(0), getIntInput(1), getIntInput(2)))
    }
}