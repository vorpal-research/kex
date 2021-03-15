package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import java.util.*

object RandomSlicer : Transformer<RandomSlicer> {
    private val random = Random(17)
    private var size = 0

    override fun transform(ps: PredicateState): PredicateState {
        size = ps.size
        return super.transform(ps).simplify()
    }

    override fun transformBase(predicate: Predicate): Predicate = when {
        random.nextDouble() < (2.0 / size) -> nothing()
        else -> predicate
    }
}

class DeltaDebugger(private val attempmts: Int, private val fails: Int = 10, val predicate: (PredicateState) -> Boolean) {
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
                    log.debug("Too many failed attempts in a row, stopping reduction")
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

fun reduceState(ps: PredicateState, attempts: Int, predicate: (PredicateState) -> Boolean) =
        DeltaDebugger(attempts, attempts, predicate).reduce(ps)

fun reduceState(ps: PredicateState, attempts: Int, allowedFails: Int, predicate: (PredicateState) -> Boolean) =
        DeltaDebugger(attempts, allowedFails, predicate).reduce(ps)