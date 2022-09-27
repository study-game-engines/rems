package me.anno.graph.types

import me.anno.graph.Node
import me.anno.graph.NodeInput
import me.anno.graph.NodeOutput
import me.anno.graph.types.flow.actions.PrintNode
import me.anno.graph.types.flow.config.ConfigGetBoolNode
import me.anno.graph.types.flow.config.ConfigGetFloatNode
import me.anno.graph.types.flow.config.ConfigGetIntNode
import me.anno.graph.types.flow.control.ForNode
import me.anno.graph.types.flow.control.IfElseNode
import me.anno.graph.types.flow.control.WhileNode
import me.anno.graph.types.flow.local.GetLocalVariableNode
import me.anno.graph.types.flow.local.SetLocalVariableNode
import me.anno.graph.types.flow.maths.*
import me.anno.io.ISaveable.Companion.registerCustomClass

class NodeLibrary(val nodes: Collection<() -> Node>) {

    constructor(vararg nodes: () -> Node) :
            this(nodes.toList())

    fun register() {
        for (node in nodes) {
            registerCustomClass(node)
        }
    }

    companion object {

        val flowNodes = NodeLibrary(
            { GetLocalVariableNode() },
            { SetLocalVariableNode() },
            { ConfigGetBoolNode() },
            { ConfigGetIntNode() },
            { ConfigGetFloatNode() },
            { ForNode() },
            { IfElseNode() },
            { WhileNode() },
            { PrintNode() },
            { MathL1Node() },
            { MathL2Node() },
            { MathL3Node() },
            { MathD1Node() },
            { MathD2Node() },
            { MathD3Node() },
            { CompareNode() }
        )

        fun init() {
            registerCustomClass { NodeInput() }
            registerCustomClass { NodeOutput() }
            flowNodes.register()
        }

    }
}