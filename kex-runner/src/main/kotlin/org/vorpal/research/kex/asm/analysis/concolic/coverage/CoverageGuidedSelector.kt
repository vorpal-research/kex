package org.vorpal.research.kex.asm.analysis.concolic.coverage

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelector
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelectorManager
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.Instruction

class CoverageGuidedSelectorManager(
    override val ctx: ExecutionContext,
    override val targets: Set<Method>
) : ConcolicPathSelectorManager {
    val executionGraph = ExecutionGraph(ctx, targets)

    override fun createPathSelectorFor(target: Method): ConcolicPathSelector =
        CoverageGuidedSelector(this)

    private val targetInstructions = targets.flatMapTo(mutableSetOf()) { it.body.flatten() }
    private val coveredInstructions = mutableSetOf<Instruction>()

    fun addCoverage(trace: List<Instruction>) {
        coveredInstructions += trace
    }

    fun isCovered(): Boolean = coveredInstructions.containsAll(targetInstructions)
    fun isCovered(instruction: Instruction): Boolean = instruction in coveredInstructions
}

class CoverageGuidedSelector(
    private val manager: CoverageGuidedSelectorManager
) : ConcolicPathSelector {
    override val ctx: ExecutionContext
        get() = manager.ctx
    private val executionGraph get() = manager.executionGraph
    private val candidates = mutableMapOf<PersistentSymbolicState, CandidateState>()

    override suspend fun isEmpty(): Boolean = manager.isCovered() || executionGraph.candidates.isEmpty()

    override suspend fun hasNext(): Boolean = !isEmpty()

    override suspend fun next(): Pair<Method, PersistentSymbolicState> {
        val candidate = executionGraph.candidates.nextCandidate()
        candidates[candidate.state] = candidate
        return candidate.method to candidate.state
    }

    override suspend fun addExecutionTrace(
        method: Method,
        checkedState: PersistentSymbolicState,
        result: ExecutionCompletedResult
    ) {
        manager.addCoverage(result.trace)
        executionGraph.addTrace(method, candidates[checkedState], result)
    }

    override fun reverse(pathClause: PathClause): PathClause = TODO() //pathClause.reversed()

}
