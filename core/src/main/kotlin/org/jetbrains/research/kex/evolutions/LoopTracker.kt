package org.jetbrains.research.kex.evolutions

import org.jetbrains.research.kfg.analysis.Loop

object LoopTracker { // XXX: remove this)
    private val loops: MutableMap<Loop, Int> = mutableMapOf()
    private var currentIndex = 0

    val Loop.name
        get() = "%loop.${loops.getOrPut(this) { ++currentIndex }}"

    fun clean() {
        currentIndex = 0
        loops.clear()
    }
}