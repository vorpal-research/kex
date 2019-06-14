package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.RandomSlicer

class DeltaDebugger(val attempmts: Int, val fails: Int = 10, val predicate: (PredicateState) -> Boolean) {

    fun reduce(ps: PredicateState): PredicateState {
        var current = ps

        var failedAttempts = 0
        for (i in 0..attempmts) {
            val reduced = run {
                var temp = RandomSlicer.apply(current)
                while (temp.size >= current.size && current.isNotEmpty) temp = RandomSlicer.apply(current)
                temp
            }
            log.debug("Old size: ${current.size}, reduced size: ${reduced.size}")

            if (predicate(reduced)) {
                log.debug("Successful reduce")
                current = reduced
                failedAttempts = 0
            } else {
                log.debug("Reduce failed, rollback")
                ++failedAttempts
                if (failedAttempts > fails) {
                    log.debug("Too many failed attmpts in a row, stopping reduction")
                    log.debug("Resulting state: $current")
                    break
                }
            }
        }

        log.debug("Reduced $ps")
        log.debug("To $current")
        return current
    }
}

fun reduceState(ps: PredicateState, attempts: Int, predicate: (PredicateState) -> Boolean)
        = DeltaDebugger(attempts, attempts, predicate).reduce(ps)

fun reduceState(ps: PredicateState, attempts: Int, allowedFails: Int, predicate: (PredicateState) -> Boolean)
        = DeltaDebugger(attempts, allowedFails, predicate).reduce(ps)