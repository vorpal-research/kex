package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.NullTerm
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.symbolic.*
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.BranchInst
import org.jetbrains.research.kfg.ir.value.instruction.SwitchInst
import org.jetbrains.research.kfg.ir.value.instruction.TableSwitchInst
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kthelper.logging.log

interface PathSelector {
    val traceManager: TraceManager<InstructionTrace>

    fun hasMorePaths(method: Method): Boolean
    fun addExecutionTrace(method: Method, result: ExecutionResult)
    fun getNextPath(): SymbolicState
}

class BfsPathSelectorImpl(override val traceManager: TraceManager<InstructionTrace>) : PathSelector {
    private val coveredPaths = mutableSetOf<PathCondition>()
    private val candidates = mutableSetOf<PathCondition>()
    private val deque = dequeOf<SymbolicState>()

    override fun hasMorePaths(method: Method): Boolean = deque.isNotEmpty()

    override fun addExecutionTrace(method: Method, result: ExecutionResult) {
        if (result.trace.path in coveredPaths) return
        coveredPaths += result.trace.path
        traceManager.addTrace(method, result.trace.trace)
        addCandidates(result.trace)
    }

    override fun getNextPath(): SymbolicState = deque.pollFirst()

    private fun addCandidates(state: SymbolicState) {
        val currentState = mutableListOf<Predicate>()
        val currentPath = mutableListOf<Clause>()

        for (predicate in (state.state as BasicState)) {
            if (predicate.type is PredicateType.Path) {
                val instruction = state[predicate]
                val reversed = Clause(instruction, predicate).reversed()
                if (reversed != null) {
                    val newPath = PathConditionImpl(currentPath.toList() + reversed)
                    if (newPath !in candidates) {
                        candidates += newPath
                        val new = SymbolicStateImpl(
                            BasicState(currentState.toList()),
                            newPath,
                            state.concreteValueMap.toMutableMap(),
                            state.termMap,
                            state.predicateMap,
                            InstructionTrace()
                        )
                        deque += new
                    }
                }
                currentPath += Clause(instruction, predicate)
            }
            currentState += predicate
        }
    }

    private fun Clause.reversed(): Clause? = when (instruction) {
        is BranchInst -> {
            val (cond, value) = with(predicate as EqualityPredicate) {
                lhv to (rhv as ConstBoolTerm).value
            }
            val reversed = Clause(instruction, path(instruction.location) {
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
                val candidates = switchInst.branches.keys.map { (it as IntConstant).value }.toSet()
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
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
                val candidates = switchInst.branches.keys.map { (it as IntConstant).value }.toSet() - value
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (coveredPaths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
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
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
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
                val candidates = switchInst.range.toSet() - value
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (coveredPaths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
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
                    is NullTerm -> Clause(instruction, path(instruction.location) {
                        lhv inequality null
                    })
                    is ConstBoolTerm -> {
                        Clause(instruction, path(instruction.location) {
                            lhv equality !rhv.value
                        })
                    }
                    else -> log.warn("Unknown clause $this").let { null }
                }
            }
            is InequalityPredicate -> {
                val (lhv, rhv) = pred.lhv to pred.rhv
                when (rhv) {
                    is NullTerm -> Clause(instruction, path(instruction.location) {
                        lhv equality null
                    })
                    else -> log.warn("Unknown clause $this").let { null }
                }
            }
            else -> null
        }
    }

}