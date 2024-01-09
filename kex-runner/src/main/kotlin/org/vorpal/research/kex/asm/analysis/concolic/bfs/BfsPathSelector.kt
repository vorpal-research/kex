package org.vorpal.research.kex.asm.analysis.concolic.bfs

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelector
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelectorManager
import org.vorpal.research.kex.state.predicate.DefaultSwitchPredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.NullTerm
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentClauseList
import org.vorpal.research.kex.trace.symbolic.PersistentPathCondition
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.toPersistentState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.dequeOf
import org.vorpal.research.kthelper.logging.log

class BfsPathSelectorManager(
    override val ctx: ExecutionContext,
    override val targets: Set<Method>
) : ConcolicPathSelectorManager {
    override fun createPathSelectorFor(target: Method): ConcolicPathSelector =
        BfsPathSelectorImpl(ctx, target)
}

class BfsPathSelectorImpl(
    override val ctx: ExecutionContext,
    val method: Method
) : ConcolicPathSelector {
    private val coveredPaths = mutableSetOf<PersistentPathCondition>()
    private val candidates = mutableSetOf<PersistentPathCondition>()
    private val deque = dequeOf<PersistentSymbolicState>()

    override suspend fun isEmpty(): Boolean = deque.isEmpty()
    override suspend fun hasNext(): Boolean = deque.isNotEmpty()

    override suspend fun addExecutionTrace(
        method: Method,
        checkedState: PersistentSymbolicState,
        result: ExecutionCompletedResult
    ) {
        val persistentResult = result.symbolicState.toPersistentState()
        if (persistentResult.path in coveredPaths) return
        coveredPaths += persistentResult.path
        addCandidates(persistentResult)
    }

    override suspend fun next(): Pair<Method, PersistentSymbolicState> = method to deque.pollFirst()

    override fun reverse(pathClause: PathClause): PathClause? = pathClause.reversed()

    private fun addCandidates(state: PersistentSymbolicState) {
        var currentState = PersistentClauseList()
        var currentPath = PersistentPathCondition()

        for (clause in state.clauses) {
            if (clause is PathClause) {
                val reversed = clause.reversed()
                if (reversed != null) {
                    val newPath = currentPath + reversed
                    if (newPath !in candidates) {
                        candidates += newPath
                        val new = persistentSymbolicState(
                            currentState + reversed,
                            newPath,
                            state.concreteTypes,
                            state.concreteValues,
                            state.termMap
                        )
                        deque += new
                    }
                }
                currentPath += clause
            }
            currentState += clause
        }
    }

    private fun PathClause.reversed(): PathClause? = when (instruction) {
        is BranchInst -> {
            val (cond, value) = with(predicate as EqualityPredicate) {
                lhv to (rhv as ConstBoolTerm).value
            }
            val reversed = copy(predicate = path(instruction.location) {
                cond equality !value
            })
            if (coveredPaths.any { reversed in it }) null
            else reversed
        }

        is SwitchInst -> when (predicate) {
            is DefaultSwitchPredicate -> {
                val defaultSwitch = predicate as DefaultSwitchPredicate
                val switchInst = instruction as SwitchInst
                val cond = defaultSwitch.cond
                val candidates = switchInst.branches.keys.mapTo(mutableSetOf()) { (it as IntConstant).value }
                var result: PathClause? = null
                for (candidate in candidates) {
                    val mutated = copy(predicate = path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (coveredPaths.any { mutated in it }) null else mutated
                }
                result
            }

            is EqualityPredicate -> {
                val equalityPredicate = predicate as EqualityPredicate
                val switchInst = instruction as SwitchInst
                val (cond, value) = equalityPredicate.lhv to (equalityPredicate.rhv as ConstIntTerm).value
                val candidates = switchInst.branches.keys.mapTo(mutableSetOf()) { (it as IntConstant).value }
                candidates.remove(value)
                var result: PathClause? = null
                for (candidate in candidates) {
                    val mutated = copy(predicate = path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (coveredPaths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = copy(predicate = path(instruction.location) {
                        cond `!in` switchInst.branches.keys.map { value(it) }
                    })
                    if (coveredPaths.any { mutated in it }) mutated else null
                }
            }

            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }

        is TableSwitchInst -> when (predicate) {
            is DefaultSwitchPredicate -> {
                val defaultSwitch = predicate as DefaultSwitchPredicate
                val switchInst = instruction as TableSwitchInst
                val cond = defaultSwitch.cond
                val candidates = switchInst.range.toSet()
                var result: PathClause? = null
                for (candidate in candidates) {
                    val mutated = copy(predicate = path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (coveredPaths.any { mutated in it }) null else mutated
                }
                result
            }

            is EqualityPredicate -> {
                val equalityPredicate = predicate as EqualityPredicate
                val switchInst = instruction as TableSwitchInst
                val (cond, value) = equalityPredicate.lhv to (equalityPredicate.rhv as ConstIntTerm).value
                val candidates = switchInst.range.toMutableSet()
                candidates.remove(value)
                var result: PathClause? = null
                for (candidate in candidates) {
                    val mutated = copy(predicate = path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (coveredPaths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = copy(predicate = path(instruction.location) {
                        cond `!in` switchInst.range.map { const(it) }
                    })
                    if (coveredPaths.any { mutated in it }) null else mutated
                }
            }

            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }

        else -> when (val pred = predicate) {
            is EqualityPredicate -> {
                val (lhv, rhv) = pred.lhv to pred.rhv
                when (rhv) {
                    is NullTerm -> copy(predicate = path(instruction.location) {
                        lhv inequality null
                    })

                    is ConstBoolTerm -> copy(predicate = path(instruction.location) {
                        lhv equality !rhv.value
                    })

                    else -> log.warn("Unknown clause $this").let { null }
                }
            }

            is InequalityPredicate -> {
                val (lhv, rhv) = pred.lhv to pred.rhv
                when (rhv) {
                    is NullTerm -> copy(predicate = path(instruction.location) {
                        lhv equality null
                    })

                    else -> log.warn("Unknown clause $this").let { null }
                }
            }

            else -> null
        }
    }
}
