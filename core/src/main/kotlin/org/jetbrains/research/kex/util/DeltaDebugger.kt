package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.RandomSlicer

class DeltaDebugger(val attempmts: Int, val predicate: (PredicateState) -> Boolean) {

    fun reduce(ps: PredicateState): PredicateState {
        var current = ps

        for (i in 0..attempmts) {
            val reduced = run {
                var temp = RandomSlicer.apply(current)
                while (temp.isEmpty || temp.size < current.size) temp = RandomSlicer.apply(current)
                temp
            }
            log.debug("Delta: old size: ${current.size}, reduced size: ${reduced.size}")

            if (predicate(reduced)) {
                log.debug("Delta: successful reduce")
                current = reduced
            } else {
                log.debug("Delta: reduce failed, rollback")
            }
        }

        log.debug("Reduced $ps\nTo $current")
        return current
    }
}