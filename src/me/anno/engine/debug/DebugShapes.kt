package me.anno.engine.debug

import me.anno.Time

object DebugShapes {

    val debugPoints = ArrayList<DebugPoint>()
    val debugLines = ArrayList<DebugLine>()
    val debugRays = ArrayList<DebugRay>()
    val debugTexts = ArrayList<DebugText>()
    val debugAABBs = ArrayList<DebugAABB>()
    val collections = arrayListOf(
        debugPoints,
        debugLines,
        debugRays,
        debugTexts,
        debugAABBs
    )

    fun clear() {
        for (i in collections.indices) {
            collections[i].clear()
        }
    }

    fun removeExpired() {
        val time = Time.nanoTime
        for (i in collections.indices) {
            collections[i].removeIf { it.timeOfDeath < time }
        }
    }
}