package org.jetbrains.research.kex.asm.analysis.concolic.cgs

import kotlinx.coroutines.yield
import org.jetbrains.research.kex.asm.analysis.concolic.PathSelector
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.NullTerm
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.symbolic.*
import org.jetbrains.research.kex.util.dropLast
import org.jetbrains.research.kex.util.nextOrNull
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.BranchInst
import org.jetbrains.research.kfg.ir.value.instruction.SwitchInst
import org.jetbrains.research.kfg.ir.value.instruction.TableSwitchInst
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class ContextGuidedSelector(override val traceManager: TraceManager<InstructionTrace>) : PathSelector {
    val executionTree = ExecutionTree()
    var currentDepth = 0
        private set
    var k = 1
        private set
    private var branchIterantor: Iterator<PathVertex> = listOf<PathVertex>().iterator()
    private var currentContext: Context? = null
    private val visitedContexts = mutableSetOf<Context>()

    override suspend fun hasNext(): Boolean {
        do {
            yield()
            val next = nextEdge() ?: continue
            when (val context = executionTree.contexts(next, k).firstOrNull { it !in visitedContexts }) {
                null -> continue
                else -> {
                    currentContext = context
                    return true
                }
            }
        } while (currentDepth <= executionTree.depth && k <= executionTree.depth)
        return false
    }

    override suspend fun next(): SymbolicState {
        val context = currentContext!!
        visitedContexts += context
        currentContext = null

        val path = context.fullPath.dropLast(1)
        val reversed = context.fullPath.last()
        val state = context.symbolicState.state.takeWhile {
            it == reversed.predicate
        }.filter { it.type !is PredicateType.Path }

        return SymbolicStateImpl(
            state,
            PathCondition(path + reversed.reversed()!!),
            context.symbolicState.concreteValueMap,
            context.symbolicState.termMap,
            context.symbolicState.predicateMap,
            context.symbolicState.trace
        )
    }

    private fun nextEdge(): PathVertex? {
        when {
            branchIterantor.hasNext() -> {}
            currentDepth < executionTree.depth -> {
                ++currentDepth
                recomputeBranches()
            }
            else -> {
                ++k
                currentDepth = 0
                recomputeBranches()
            }
        }
        return branchIterantor.nextOrNull()
    }

    private fun recomputeBranches() {
        branchIterantor = executionTree.getBranches(currentDepth).shuffled().iterator()
    }

    override suspend fun addExecutionTrace(method: Method, result: ExecutionResult) {
        executionTree.addTrace(result.trace)
    }

    private fun Clause.reversed(): Clause? = when (instruction) {
        is BranchInst -> {
            val (cond, value) = with(predicate as EqualityPredicate) {
                lhv to (rhv as ConstBoolTerm).value
            }
            val reversed = Clause(instruction, path(instruction.location) {
                cond equality !value
            })
            reversed
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
                    result = mutated
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
                    result = mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond `!in` switchInst.branches.keys.map { value(it) }
                    })
                    mutated
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
                    result = mutated
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
                    result = mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond `!in` switchInst.range.map { const(it) }
                    })
                    mutated
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

    fun view() {
        executionTree.view("tree", "/usr/bin/dot", "/usr/bin/firefox")
    }
}