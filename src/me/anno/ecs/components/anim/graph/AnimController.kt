package me.anno.ecs.components.anim.graph

import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.annotations.DebugAction
import me.anno.ecs.annotations.Docs
import me.anno.ecs.components.anim.AnimRenderer
import me.anno.ecs.prefab.PrefabCache
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.ui.render.PlayMode
import me.anno.engine.ui.scenetabs.ECSSceneTabs
import me.anno.graph.types.states.StateMachine
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.serialization.NotSerializedProperty

@Docs("Controls animations using a state machine like in Unity")
class AnimController : Component() {

    @Docs("Source file for animation graph")
    var graphSource: FileReference = InvalidRef

    @NotSerializedProperty
    var graphInstance: StateMachine? = null

    @NotSerializedProperty
    private var renderer: AnimRenderer? = null

    @NotSerializedProperty
    private var lastGraphSource: FileReference = InvalidRef

    @Docs("Whether the animation graph is allowed to load async")
    var asyncLoading = true

    fun loadGraph(): StateMachine? {
        if (lastGraphSource == graphSource) return graphInstance
        val graph = graphSource
        val prefab = PrefabCache[graph, asyncLoading] ?: return null // wait
        lastGraphSource = graph
        graphInstance = prefab.createInstance() as? StateMachine
        return graphInstance
    }

    @DebugAction
    fun openEditor() {
        ECSSceneTabs.open(graphSource, PlayMode.EDITING, true)
    }

    override fun onEnable() {
        super.onEnable()
        onChangeStructure(entity ?: return)
    }

    override fun onChangeStructure(entity: Entity) {
        super.onChangeStructure(entity)
        renderer = entity.getComponent(AnimRenderer::class)
    }

    override fun onUpdate(): Int {
        val renderer = renderer
        if (renderer == null) { // wait for renderer
            lastWarning = "Renderer missing"
            return 5
        }
        val graph = loadGraph()
        if (graph == null) { // wait for a graph
            lastWarning = "Graph missing"
            return 5
        }
        val newState = graph.update()
        if (newState is AnimStateNode) {
            newState.updateRenderer(graph.prevState as? AnimStateNode, renderer)
        } else lastWarning = "Graph is missing default state"
        return 1
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as AnimController
        clone.graphSource = graphSource
    }

    override val className get() = "AnimController"

}