package org.vorpal.research.kex.asm.analysis.concolic.legacy

import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.state.BasicState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.inverse
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.dequeOf
import org.vorpal.research.kthelper.logging.log

interface SearchStrategy {
    val method: Method
    val paths: Set<PredicateState>

    fun next(state: PredicateState): PredicateState?
}

@Suppress("unused")
class BfsStrategy(override val method: Method, override val paths: Set<PredicateState>) : SearchStrategy {
    private val random = EasyRandomDriver()
    override fun next(state: PredicateState): PredicateState? {
        val basic = state as? BasicState ?: unreachable { log.error("Only basic states can be mutated") }
        val currentState = StateBuilder()
        val currentPath = StateBuilder()

        for (predicate in basic.predicates) {
            when (predicate.type) {
                is PredicateType.Path -> {
                    val inverted = predicate.inverse(random)
                    val newPath = currentPath + inverted
                    if (paths.all { !it.startsWith(newPath.apply()) }) {
                        currentState += inverted
                        return currentState.apply()
                    } else {
                        currentState += predicate
                        currentPath += predicate
                    }
                }
                else -> {
                    currentState += predicate
                }
            }
        }
        return null
    }
}

@Suppress("unused")
class DfsStrategy(override val method: Method, override val paths: Set<PredicateState>) : SearchStrategy {
    private val random = EasyRandomDriver()

    override fun next(state: PredicateState): PredicateState? {
        val currentState = dequeOf((state as BasicState).predicates)
        val currentPath = dequeOf(currentState.filter { it.type is PredicateType.Path })
        while (currentState.isNotEmpty()) {
            val last = currentState.pollLast()
            if (last == currentPath.peekLast()) {
                currentPath.pollLast()
            }

            if (last.type is PredicateType.Path) {
                val inverted = last.inverse(random)
                val newPath = BasicState(currentPath.toList() + inverted)

                if (paths.all { !it.startsWith(newPath) }) {
                    currentState += inverted
                    return BasicState(currentState.toList())
                }
            }
        }
        return null
    }
}
