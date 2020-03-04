package org.jetbrains.research.kex.smt.boolector

import com.abdullin.kthelper.assert.ktassert
import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.boolector.Btor
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexReal
import org.jetbrains.research.kex.smt.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.ConstLongTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.collectPointers
import org.jetbrains.research.kex.state.transformer.collectVariables
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kfg.type.TypeFactory

private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)

@Solver("boolector")
class BoolectorSolver(val tf: TypeFactory) : AbstractSMTSolver {
    val ef = BoolectorExprFactory()

    override fun isReachable(state: PredicateState) =
            isPathPossible(state, state.path)

    override fun isPathPossible(state: PredicateState, path: PredicateState) = isViolated(state, path)

    override fun isViolated(state: PredicateState, query: PredicateState): Result {
        if (logQuery) {
            log.run {
                debug("Boolector solver check")
                debug("State: $state")
                debug("Query: $query")
            }
        }

        val ctx = BoolectorContext(ef, (1 shl 8) + 1, (1 shl 24) + 1)

        val converter = BoolectorConverter(tf)
        val boolectorState = converter.convert(state, ef, ctx)
        val boolectorQuery = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(boolectorState, boolectorQuery)
        log.debug("Check finished")
        return when (result) {
            Btor.Status.UNSAT -> Result.UnsatResult
            Btor.Status.UNKNOWN -> Result.UnknownResult("should not happen")
            Btor.Status.SAT -> Result.SatResult(collectModel(ctx, state))
        }
    }

    private fun check(state: Bool_, query: Bool_): Btor.Status {
        val (state_, query_) = state to query

        state_.asAxiom().assertForm()
        query_.axiom.assertForm()
        query_.expr.assertForm()

        if (logFormulae) {
            log.debug(ef.ctx.dumpSmt2())
        }
        log.debug("Running Boolector solver")
        val result = ef.ctx.check() ?: unreachable { log.error("Solver error") }

        log.debug("Solver finished")

        return result
    }

    private fun collectModel(ctx: BoolectorContext, vararg states: PredicateState): SMTModel {
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.map {
            val expr = BoolectorConverter(tf).convert(it, ef, ctx)
            val boolectorExpr = expr.expr

            // this is needed because boolector represents real numbers as integers
            val undone = BoolectorUnlogic.undo(boolectorExpr)
            val actualValue = when {
                it.type is KexReal -> when (undone) {
                    is ConstIntTerm -> term { const(undone.value.toFloat()) }
                    is ConstLongTerm -> term { const(undone.value.toDouble()) }
                    else -> unreachable {
                        log.error("Unexpected integral term when trying to reanimate floating point value: $undone")
                    }
                }
                else -> undone
            }
            it to actualValue
        }.toMap().toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val bounds = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()

        for (ptr in ptrs) {
            val memspace = ptr.memspace

            val startMem = ctx.getInitialMemory(memspace)
            val endMem = ctx.getMemory(memspace)

            val startBounds = ctx.getBounds(memspace)
            val endBounds = ctx.getBounds(memspace)

            val eptr = BoolectorConverter(tf).convert(ptr, ef, ctx) as? Ptr_
                    ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

            val startV = startMem.load(eptr, BoolectorExprFactory.getTypeSize(ptr.type))
            val endV = endMem.load(eptr, BoolectorExprFactory.getTypeSize(ptr.type))

            val startB = startBounds[eptr]
            val endB = endBounds[eptr]


            val modelPtr = BoolectorUnlogic.undo(eptr.expr)
            val modelStartV = BoolectorUnlogic.undo(startV.expr)
            val modelEndV = BoolectorUnlogic.undo(endV.expr)
            val modelStartB = BoolectorUnlogic.undo(startB.expr)
            val modelEndB = BoolectorUnlogic.undo(endB.expr)

            memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
            memories.getValue(memspace).first[modelPtr] = modelStartV
            memories.getValue(memspace).second[modelPtr] = modelEndV

            bounds.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
            bounds.getValue(memspace).first[modelPtr] = modelStartB
            bounds.getValue(memspace).second[modelPtr] = modelEndB

            ktassert(assignments.getOrPut(ptr) { modelPtr } == modelPtr)
        }

        return SMTModel(assignments,
                memories.map { it.key to MemoryShape(it.value.first, it.value.second) }.toMap(),
                bounds.map { it.key to MemoryShape(it.value.first, it.value.second) }.toMap())
    }

    override fun cleanup() = ef.ctx.release()
}
