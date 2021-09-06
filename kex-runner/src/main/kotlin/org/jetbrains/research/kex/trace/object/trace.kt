package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kex.trace.AbstractTrace
import org.jetbrains.research.kfg.ir.BasicBlock

data class ActionTrace(val actions: List<Action>) : AbstractTrace(), Iterable<Action> by actions {
    fun isCovered(bb: BasicBlock) = this.any { it is BlockAction && it.block == bb }
}