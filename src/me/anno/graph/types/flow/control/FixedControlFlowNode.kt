package me.anno.graph.types.flow.control

import me.anno.graph.NodeOutput
import me.anno.graph.types.flow.ControlFlowNode

abstract class FixedControlFlowNode : ControlFlowNode {

    constructor(name: String) : super(name)

    constructor(name: String, inputs: List<String>, outputs: List<String>) : super(name, inputs, outputs)

    constructor(
        name: String,
        numFlowInputs: Int,
        otherInputs: List<String>,
        numFlowOutputs: Int,
        otherOutputs: List<String>
    ) : super(name, numFlowInputs, otherInputs, numFlowOutputs, otherOutputs)

    fun getOutputNodes(index: Int): NodeOutput {
        val c = outputs!![index] // NodeOutputs
        if (c.type != "Flow") throw RuntimeException()
        return c
    }

    override fun canAddInput(): Boolean = false
    override fun canAddOutput(): Boolean = false
    override fun canRemoveInput(): Boolean = false
    override fun canRemoveOutput(): Boolean = false

}