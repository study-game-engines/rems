package me.anno.graph

import me.anno.io.ISaveable
import me.anno.io.NamedSaveable
import me.anno.io.base.BaseWriter
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.style.Style
import org.joml.Vector3d

abstract class Node : NamedSaveable() {

    abstract fun createUI(list: PanelListY, style: Style)

    val position = Vector3d()

    // multiple layers would be great for large functions :D
    // even though they really should be split...
    // but we may zoom into other functions :)
    var layer = 0

    var inputs: Array<NodeInput>? = null
    var outputs: Array<NodeOutput>? = null

    abstract fun canAddInput(): Boolean
    abstract fun canAddOutput(): Boolean
    abstract fun canRemoveInput(): Boolean
    abstract fun canRemoveOutput(): Boolean

    fun setOutput(value: Any?, index: Int) {
        val node = outputs!![index]
        node.value = value
        node.others.forEach { it.invalidate() }
    }

    // the node ofc needs to save its custom content and behaviour as well
    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObjectArray(this, "inputs", inputs)
        writer.writeObjectArray(this, "outputs", outputs)
        writer.writeInt("layer", layer)
        writer.writeVector3d("position", position)
    }

    override fun readObjectArray(name: String, values: Array<ISaveable?>) {
        when (name) {
            "inputs" -> inputs = values.filterIsInstance<NodeInput>().toTypedArray()
            "outputs" -> outputs = values.filterIsInstance<NodeOutput>().toTypedArray()
            else -> super.readObjectArray(name, values)
        }
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "layer" -> layer = value
            else -> super.readInt(name, value)
        }
    }

    override fun readVector3d(name: String, value: Vector3d) {
        when (name) {
            "position" -> position.set(value)
            else -> super.readVector3d(name, value)
        }
    }

    fun connectTo(otherNode: Node) {
        connectTo(0, otherNode, 0)
    }

    fun connectTo(otherNode: Node, othersInputIndex: Int) {
        connectTo(0, otherNode, othersInputIndex)
    }

    fun connectTo(outputIndex: Int, otherNode: Node, othersInputIndex: Int) {

        val output = outputs!![outputIndex]
        // todo check if the node connector can have multiple outputs
        // flow only can have one,
        // values can have many

        val input = otherNode.inputs!![othersInputIndex]
        // todo check if the node connector can have multiple inputs
        // flow can have many,
        // values only can have one

        output.others += input
        input.others += output

    }

    fun setInput(index: Int, value: Any?, validId: Int) {
        val c = inputs!![index]
        c.lastValidId = validId
        c.value = value
    }

    fun setInput(index: Int, value: Any?) {
        setInput(index, value, -1)
    }

    fun setInputs(inputValues: List<Any?>, validId: Int) {
        for ((index, value) in inputValues.withIndex()) {
            setInput(index, value, validId)
        }
    }

    fun setInputs(inputValues: List<Any?>) {
        setInputs(inputValues, -1)
    }

}