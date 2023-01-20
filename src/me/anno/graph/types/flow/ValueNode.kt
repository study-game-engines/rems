package me.anno.graph.types.flow

import me.anno.graph.NodeOutput
import me.anno.graph.types.FlowGraph

abstract class ValueNode : FlowGraphNode {

    constructor(name: String) : super(name)
    constructor(name: String, inputs: List<String>, outputs: List<String>) : super(name, inputs, outputs)

    abstract fun compute(graph: FlowGraph)

    override fun execute(graph: FlowGraph): NodeOutput? {
        compute(graph)
        return null
    }

}