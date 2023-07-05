package org.vorpal.research.kex.asm.analysis.crash.precondition

import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kthelper.logging.log

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
    val ready: Boolean

    fun getNewPreconditions(): Map<Pair<Instruction, TraverserState>, Set<PersistentSymbolicState>>
    fun getPreconditions(location: Instruction, state: TraverserState): Set<PersistentSymbolicState>
}

interface ExceptionPreconditionReceiver<T> {
    fun addPrecondition(precondition: T)
}

class ExceptionPreconditionChannel<T>(
    val name: String,
    val builder: ExceptionPreconditionBuilder<T>,
    private var readyInternal: Boolean
) : ExceptionPreconditionProvider<T>, ExceptionPreconditionReceiver<T> {
    private val mappings = mutableMapOf<Pair<Instruction, TraverserState>, MutableSet<PersistentSymbolicState>>()
    override val ready: Boolean
        get() = synchronized(lock) { readyInternal }

    override val targetException: Class
        get() = builder.targetException

    override var hasNewPreconditions = false
        private set

    private val lock = Any()

    override fun addPrecondition(precondition: T): Unit = synchronized(lock) {
        if (!readyInternal) {
            log.debug("Channel $name is ready")
        }
        readyInternal = true
        hasNewPreconditions = hasNewPreconditions || builder.addPrecondition(precondition)
    }

    override fun getNewPreconditions(): Map<Pair<Instruction, TraverserState>, Set<PersistentSymbolicState>> =
        synchronized(lock) {
            when {
                !readyInternal -> emptyMap()
                hasNewPreconditions -> mappings
                    .mapValues { (key, _) -> getPreconditions(key.first, key.second) }
                    .filterValues { it.isNotEmpty() }
                    .also { hasNewPreconditions = false }
                else -> emptyMap()
            }
        }

    override fun getPreconditions(location: Instruction, state: TraverserState): Set<PersistentSymbolicState> =
        synchronized(lock) {
            when {
                !readyInternal -> {
                    mappings.getOrPut(location to state, ::mutableSetOf)
                    return emptySet()
                }
                else -> {
                    val preconditions = builder.build(location, state)
                    val uncheckedPreconditions = preconditions - mappings.getOrPut(location to state, ::mutableSetOf)
                    mappings[location to state]!!.addAll(preconditions)
                    return uncheckedPreconditions
                }
            }
        }
}
