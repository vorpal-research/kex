package org.vorpal.research.kex.trace.`object`

import org.vorpal.research.kex.asm.manager.BlockWrapper
import org.vorpal.research.kex.trace.AbstractTrace

data class ActionTrace(val actions: List<Action>) : AbstractTrace(), Iterable<Action> by actions {
    fun isCovered(bb: BlockWrapper) = this.any { it is BlockAction && it.block == bb }
}