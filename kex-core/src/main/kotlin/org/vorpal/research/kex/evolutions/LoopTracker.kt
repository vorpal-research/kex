package org.vorpal.research.kex.evolutions

import org.vorpal.research.kfg.visitor.Loop

object LoopTracker { // XXX: remove this)
    private val loops: MutableMap<Loop, Int> = mutableMapOf()
    private var currentIndex = 0

    val Loop.name
        get() = "%loop.${loops.getOrPut(this) { ++currentIndex }}"

    @Suppress("unused")
    fun clean() {
        currentIndex = 0
        loops.clear()
    }
}
