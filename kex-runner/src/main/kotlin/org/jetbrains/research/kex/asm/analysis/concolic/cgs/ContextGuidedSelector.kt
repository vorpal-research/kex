package org.jetbrains.research.kex.asm.analysis.concolic.cgs

import kotlinx.coroutines.yield
import org.jetbrains.research.kex.asm.analysis.concolic.PathSelector
import org.jetbrains.research.kex.asm.manager.NoConcreteInstanceException
import org.jetbrains.research.kex.asm.manager.instantiationManager
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.InstanceOfTerm
import org.jetbrains.research.kex.state.term.NullTerm
import org.jetbrains.research.kex.state.term.numericValue
import org.jetbrains.research.kex.state.transformer.TermCollector
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.symbolic.*
import org.jetbrains.research.kex.util.dropLast
import org.jetbrains.research.kex.util.nextOrNull
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.BranchInst
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.SwitchInst
import org.jetbrains.research.kfg.ir.value.instruction.TableSwitchInst
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class ContextGuidedSelector(
    override val tf: TypeFactory,
    override val traceManager: TraceManager<InstructionTrace>
) : PathSelector {
    val executionTree = ExecutionTree()
    var currentDepth = 0
        private set
    var k = 1
        private set
    private var branchIterator: Iterator<PathVertex> = listOf<PathVertex>().iterator()
    private val visitedContexts = mutableSetOf<Context>()
    private var state: State? = null

    private class State(
        val context: Context,
        val path: List<Clause>,
        val activeClause: Clause,
        val revertedClause: Clause
    )

    override suspend fun hasNext(): Boolean {
        do {
            yield()
            val next = nextEdge() ?: continue
            if (executionTree.isExhausted(next)) continue
            when (val context = executionTree.contexts(next, k).firstOrNull { it !in visitedContexts }) {
                null -> continue
                else -> {
                    val path = context.fullPath.dropLast(1)
                    val activeClause = context.fullPath.lastOrNull() ?: continue
                    val revertedClause = activeClause.reversed() ?: continue
                    state = State(context, path, activeClause, revertedClause)
                    return true
                }
            }
        } while (currentDepth <= executionTree.depth && k <= executionTree.depth)
        return false
    }

    override suspend fun next(): SymbolicState {
        val currentState = state!!
        visitedContexts += currentState.context
        state = null

        val state = currentState.context.symbolicState.state.takeWhile {
            it == currentState.activeClause.predicate
        }.filter { it.type !is PredicateType.Path }

        return SymbolicStateImpl(
            state,
            PathCondition(currentState.path + currentState.revertedClause),
            currentState.context.symbolicState.concreteValueMap,
            currentState.context.symbolicState.termMap,
            currentState.context.symbolicState.predicateMap,
            currentState.context.symbolicState.trace
        )
    }

    private fun nextEdge(): PathVertex? {
        when {
            branchIterator.hasNext() -> {}
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
        return branchIterator.nextOrNull()
    }

    private fun recomputeBranches() {
        branchIterator = executionTree.getBranches(currentDepth).shuffled().iterator()
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
        is SwitchInst -> when (val pred = predicate) {
            is DefaultSwitchPredicate -> {
                val switchInst = instruction as SwitchInst
                val cond = pred.cond
                val candidates = switchInst.branches.keys.map { (it as IntConstant).value }.toSet()
                candidates.randomOrNull()?.let {
                    Clause(instruction, path(instruction.location) {
                        cond equality it
                    })
                }
            }
            is EqualityPredicate -> {
                val equalityPredicate = predicate as EqualityPredicate
                val switchInst = instruction as SwitchInst
                val cond = equalityPredicate.lhv


                val outgoingPaths = switchInst.branches.toList()
                    .groupBy({ it.second }, { it.first })
                    .map { it.value.map { (it as IntConstant).value }.toSet() }

                val equivalencePaths = mutableMapOf<Int, Set<Int>>()
                for (set in outgoingPaths) {
                    for (value in set) {
                        equivalencePaths[value] = set
                    }
                }

                val visitedCandidates = executionTree.getPathVertex(this).predecessors
                    .asSequence()
                    .flatMap { it.successors }
                    .map { it.clause.predicate }
                    .filterIsInstance<EqualityPredicate>()
                    .map { it.rhv.numericValue }
                    .toSet()

                val candidates = run {
                    val currentRange = switchInst.branches.keys.map { (it as IntConstant).value }.toMutableSet()
                    for (candidate in visitedCandidates) {
                        currentRange.removeAll(equivalencePaths[candidate]!!)
                    }
                    currentRange
                }

                candidates.randomOrNull()?.let {
                    Clause(instruction, path(instruction.location) {
                        cond equality it
                    })
                }
            }
            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }
        is TableSwitchInst -> when (val pred = predicate) {
            is DefaultSwitchPredicate -> {
                val switchInst = instruction as TableSwitchInst
                val cond = pred.cond
                val candidates = switchInst.range.toSet()
                candidates.randomOrNull()?.let {
                    Clause(instruction, path(instruction.location) {
                        cond equality it
                    })
                }
            }
            is EqualityPredicate -> {
                val switchInst = instruction as TableSwitchInst
                val cond = pred.lhv

                val outgoingPaths = switchInst.range
                    .zip(switchInst.branches)
                    .groupBy({ it.second }, { it.first })
                    .map { it.value.toSet() }

                val equivalencePaths = mutableMapOf<Int, Set<Int>>()
                for (set in outgoingPaths) {
                    for (value in set) {
                        equivalencePaths[value] = set
                    }
                }

                val visitedCandidates = executionTree.getPathVertex(this).predecessors
                    .asSequence()
                    .flatMap { it.successors }
                    .map { it.clause.predicate }
                    .filterIsInstance<EqualityPredicate>()
                    .map { it.rhv.numericValue }
                    .toSet()

                val candidates = run {
                    val currentRange = switchInst.range.toMutableSet()
                    for (candidate in visitedCandidates) {
                        currentRange.removeAll(equivalencePaths[candidate]!!)
                    }
                    currentRange
                }

                candidates.randomOrNull()?.let {
                    Clause(instruction, path(instruction.location) {
                        cond equality it
                    })
                }
            }
            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }
        is CallInst -> when (val pred = predicate) {
            is EqualityPredicate -> when (val lhv = pred.lhv) {
                is InstanceOfTerm -> {
                    val termType = lhv.operand.type.getKfgType(tf)
                    val excludeClasses = executionTree.getPathVertex(this).predecessors
                        .asSequence()
                        .flatMap { it.successors }
                        .map { it.clause.predicate }
                        .flatMap { TermCollector.getFullTermSet(it).filterIsInstance<InstanceOfTerm>() }
                        .map { it.checkedType.getKfgType(tf) }
                        .filterIsInstance<ClassType>()
                        .map { it.klass }
                        .toSet()
                    try {
                        val newType = instantiationManager.get(tf, termType, excludeClasses)
                        Clause(instruction, path(instruction.location) {
                            (lhv.operand `is` newType.kexType) equality true
                        })
                    } catch (e: NoConcreteInstanceException) {
                        executionTree.markExhausted(this)
                        null
                    }
                }
                else -> when (val rhv = pred.rhv) {
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
            else -> unreachable { log.error("Unexpected predicate in call clause: $predicate") }
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