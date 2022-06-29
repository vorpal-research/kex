package org.vorpal.research.kex.asm.analysis.concolic.cgs

import kotlinx.coroutines.yield
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.PathSelector
import org.vorpal.research.kex.asm.manager.NoConcreteInstanceException
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.*
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.InstanceOfTerm
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.transformer.TermCollector
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kex.util.dropLast
import org.vorpal.research.kex.util.nextOrNull
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`

class ContextGuidedSelector(
    override val ctx: ExecutionContext,
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
        val path: List<PathClause>,
        val activeClause: PathClause,
        val revertedClause: PathClause
    )

    override suspend fun isEmpty(): Boolean = executionTree.isEmpty()

    override suspend fun hasNext(): Boolean = `try` {
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
        false
    }.getOrElse { false }

    override suspend fun next(): SymbolicState {
        val currentState = state!!
        visitedContexts += currentState.context
        state = null

        val state = ClauseState(
            currentState.context.symbolicState.clauses
                .takeWhile { it != currentState.activeClause }
                .filter { it.predicate.type !is PredicateType.Path }
        )

        return SymbolicStateImpl(
            state,
            PathCondition(currentState.path + currentState.revertedClause),
            currentState.context.symbolicState.concreteValueMap,
            currentState.context.symbolicState.termMap
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
        branchIterator = executionTree.getBranches(currentDepth).shuffled(ctx.random).iterator()
    }

    override suspend fun addExecutionTrace(method: Method, result: ExecutionCompletedResult) {
        executionTree.addTrace(result.trace)
    }

    private fun PathClause.reversed(): PathClause? = when (type) {
        PathClauseType.NULL_CHECK -> copy(predicate = predicate.reverseBoolCond())
        PathClauseType.TYPE_CHECK -> {
            val lhv = predicate.operands[0] as InstanceOfTerm
            val termType = lhv.operand.type.getKfgType(ctx.types)
            val excludeClasses = executionTree.getPathVertex(this).predecessors
                .asSequence()
                .flatMap { it.successors }
                .map { it.clause.predicate }
                .flatMap { TermCollector.getFullTermSet(it).filterIsInstance<InstanceOfTerm>() }
                .map { it.checkedType.getKfgType(ctx.types) }
                .filterIsInstance<ClassType>()
                .map { it.klass }
                .toSet()

            try {
                val newType = instantiationManager.get(ctx.types, termType, excludeClasses, ctx.random)
                copy(predicate = path(instruction.location) {
                    (lhv.operand `is` newType.kexType) equality true
                })
            } catch (e: NoConcreteInstanceException) {
                executionTree.markExhausted(this)
                null
            }
        }
        PathClauseType.CONDITION_CHECK -> when (val inst = instruction) {
            is BranchInst -> copy(predicate = predicate.reverseBoolCond())
            is SwitchInst -> {
                val predecessors = executionTree.getPathVertex(this).predecessors
                predicate.reverseSwitchCond(predecessors, inst.branches)?.let {
                    copy(predicate = it)
                }
            }
            is TableSwitchInst -> {
                val predecessors = executionTree.getPathVertex(this).predecessors
                val branches = inst.range.let { range ->
                    range.associateWith { inst.branches[it - range.first] }
                        .mapKeys { ctx.values.getInt(it.key) }
                }
                predicate.reverseSwitchCond(predecessors, branches)?.let {
                    copy(predicate = it)
                }
            }
            else -> unreachable { log.error("Unexpected predicate in clause $inst") }
        }
        PathClauseType.BOUNDS_CHECK -> copy(predicate = predicate.reverseBoolCond())
    }

    private fun Predicate.reverseBoolCond() = when (this) {
        is EqualityPredicate -> predicate(this.type, this.location) {
            lhv equality !(rhv as ConstBoolTerm).value
        }
        is InequalityPredicate -> predicate(this.type, this.location) {
            lhv inequality !(rhv as ConstBoolTerm).value
        }
        else -> unreachable { log.error("Unexpected predicate in bool cond: $this") }
    }

    private fun Predicate.reverseSwitchCond(
        neighbouringVertices: Set<Vertex>,
        branches: Map<Value, BasicBlock>
    ): Predicate? = when (this) {
        is DefaultSwitchPredicate -> {
            val candidates = branches.keys.map { (it as IntConstant).value }.toSet()
            candidates.randomOrNull(ctx.random)?.let {
                predicate(this.type, this.location) {
                    cond equality it
                }
            }
        }
        is EqualityPredicate -> {
            val outgoingPaths = branches.toList()
                .groupBy({ it.second }, { it.first })
                .map { it.value.map { const -> (const as IntConstant).value }.toSet() }

            val equivalencePaths = mutableMapOf<Int, Set<Int>>()
            for (set in outgoingPaths) {
                for (value in set) {
                    equivalencePaths[value] = set
                }
            }

            val visitedCandidates = neighbouringVertices
                .asSequence()
                .flatMap { it.successors }
                .map { it.clause.predicate }
                .filterIsInstance<EqualityPredicate>()
                .map { it.rhv.numericValue }
                .toSet()

            val candidates = run {
                val currentRange = branches.keys.map { (it as IntConstant).value }.toMutableSet()
                for (candidate in visitedCandidates) {
                    currentRange.removeAll(equivalencePaths[candidate]!!)
                }
                currentRange
            }

            candidates.randomOrNull(ctx.random)?.let {
                predicate(type, location) {
                    lhv equality it
                }
            }
        }
        else -> unreachable { log.error("Unexpected predicate in switch clause: $this") }
    }

    fun view() {
        executionTree.view("tree", "/usr/bin/dot", "/usr/bin/firefox")
    }
}