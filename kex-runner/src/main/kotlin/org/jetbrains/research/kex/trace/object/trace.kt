package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kfg.ir.BasicBlock

data class Trace(val actions: List<Action>) : Iterable<Action> by actions {
    fun isCovered(bb: BasicBlock) = this.any { it is BlockAction && it.block == bb }

    override fun equals(other: Any?): Boolean {
        if (other is Trace) {
            return this.actions.zip(other.actions).map { it.first formalEquals it.second }.all { it }
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return actions.hashCode()
    }
}