package me.anno.graph

import me.anno.graph.types.FlowGraph
import me.anno.graph.types.flow.ValueNode
import me.anno.utils.types.AnyToDouble
import me.anno.utils.types.AnyToFloat
import me.anno.utils.types.AnyToInt
import me.anno.utils.types.AnyToLong

class NodeInput : NodeConnector {

    constructor() : super()
    constructor(type: String) : super(type)
    constructor(type: String, node: Node) : super(type, node)

    // ofc not thread-safe
    // for multi-thread-operation, copy the graphs
    var lastValidId = -1

    override fun invalidate() {
        super.invalidate()
        lastValidId = -1
    }

    fun castGetValue(graph: FlowGraph, validId: Int): Any? {
        if (validId == lastValidId) return value
        val src = others.firstOrNull()
        val srcNode = src?.node
        if (srcNode is ValueNode) {
            srcNode.compute(graph)
        }
        if (src != null) value = src.value
        // ensure the value is matching
        // todo cast if required
        // todo warn if failed
        lastValidId = validId
        value = when (type) {
            // ensures that the function gets the correct type
            "Int", "Integer" -> AnyToInt.getInt(value, 0, 0)
            "Long" -> AnyToLong.getLong(value, 0, 0)
            "Float" -> AnyToFloat.getFloat(value, 0, 0f)
            "Double" -> AnyToDouble.getDouble(value, 0, 0.0)
            "String" -> value.toString()
            "Any?", "", "?" -> value
            else -> TODO("type $type")
        }
        return value
    }

    override val className: String = "NodeInput"

}