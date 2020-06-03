package org.jetbrains.research.kex.asm.analysis.concolic

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.collection.dequeOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.predicate.inverse
import org.jetbrains.research.kfg.ir.Method

interface SearchStrategy {
    val method: Method
    val paths: Set<PredicateState>

    fun next(state: PredicateState): PredicateState?
}

class BfsStrategy(override val method: Method, override val paths: Set<PredicateState>) : SearchStrategy {
    override fun next(state: PredicateState): PredicateState? {
        val basic = state as? BasicState ?: unreachable { log.error("Only basic states can be mutated") }
        val currentState = StateBuilder()
        val currentPath = StateBuilder()

        for (predicate in basic.predicates) {
            when (predicate.type) {
                is PredicateType.Path -> {
                    val inverted = predicate.inverse()
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

class DfsStrategy(override val method: Method, override val paths: Set<PredicateState>) : SearchStrategy {
    override fun next(state: PredicateState): PredicateState? {
        val currentState = dequeOf(*(state as BasicState).predicates.toTypedArray())
        val currentPath = dequeOf(*currentState.filter { it.type is PredicateType.Path }.toTypedArray())
        while (currentState.isNotEmpty()) {
            val last = currentState.pollLast()
            if (last == currentPath.peekLast()) {
                currentPath.pollLast()
            }

            if (last.type is PredicateType.Path) {
                val inverted = last.inverse()
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