package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kfg.ir.BasicBlock

data class Trace(val actions: List<Action>) : Iterable<Action> by actions {
    fun isCovered(bb: BasicBlock) = this.any { it is BlockAction && it.block == bb }
}