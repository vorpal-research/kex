package org.vorpal.research.kex.asm.analysis.crash.precondition

import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.value.instruction.Instruction

interface ExceptionPreconditionBuilder<T> {
    val targetException: Class

    /**
     * @return `true` if the precondition is successfully added
     */
    fun addPrecondition(precondition: T): Boolean
    fun build(location: Instruction, state: TraverserState): Set<PersistentSymbolicState>
}

interface ExceptionPreconditionProvider<T> {
    val targetException: Class
    val hasNewPreconditions: Boolean
    fun getNewPreconditions(): Map<Pair<Instruction, TraverserState>, Set<PersistentSymbolicState>>
    fun getPreconditions(location: Instruction, state: TraverserState): Set<PersistentSymbolicState>
}

interface ExceptionPreconditionReceiver<T> {
    fun addPrecondition(precondition: T)
}

class ExceptionPreconditionChannel<T>(
    val name: String,
    val builder: ExceptionPreconditionBuilder<T>
) : ExceptionPreconditionProvider<T>, ExceptionPreconditionReceiver<T> {
    private val mappings = mutableMapOf<Pair<Instruction, TraverserState>, MutableSet<PersistentSymbolicState>>()

    override val targetException: Class
        get() = builder.targetException

    override var hasNewPreconditions = false
        private set

    private val lock = Any()

    override fun addPrecondition(precondition: T): Unit = synchronized(lock) {
        hasNewPreconditions = hasNewPreconditions || builder.addPrecondition(precondition)
    }

    override fun getNewPreconditions(): Map<Pair<Instruction, TraverserState>, Set<PersistentSymbolicState>> =
        synchronized(lock) {
            when {
                hasNewPreconditions -> mappings
                    .mapValues { (key, _) -> getPreconditions(key.first, key.second) }
                    .filterValues { it.isNotEmpty() }
                    .also { hasNewPreconditions = false }
                else -> emptyMap()
            }
        }

    override fun getPreconditions(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> =
        synchronized(lock) {
            val preconditions = builder.build(location, state)
            val uncheckedPreconditions = preconditions - mappings.getOrPut(location to state, ::mutableSetOf)
            mappings[location to state]!!.addAll(preconditions)
            return uncheckedPreconditions
        }
}
