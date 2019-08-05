package org.jetbrains.research.kex.smt.boolector

import org.jetbrains.research.boolector.BoolectorSat
import org.jetbrains.research.boolector.BoolectorSat.Status.fromInt
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.smt.AbstractSMTSolver
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.MemoryShape
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.TypeFactory

private val timeout = kexConfig.getIntValue("smt", "timeout", 3) * 1000
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)
private val simplifyFormulae = kexConfig.getBooleanValue("smt", "simplifyFormulae", false)

@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
class BoolectorSolver(val tf: TypeFactory) : AbstractSMTSolver {
    val ef = BoolectorExprFactory()

    override fun isReachable(state: PredicateState) =
            isPathPossible(state, state.filterByType(PredicateType.Path()))

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
        return when (fromInt(result.first)) {
            BoolectorSat.Status.UNSAT -> Result.UnsatResult
            BoolectorSat.Status.UNKNOWN -> Result.UnknownResult(result.second as String)
            BoolectorSat.Status.SAT -> Result.SatResult(collectModel(ctx, state))//?????????????
        } // BoolectorSat enum
    }

    private fun check(state: Bool_, query: Bool_): Pair<Int, Any> {
        val (state_, query_) = state to query
        if (logFormulae) {
            log.run {
                debug("State: $state_")
                debug("Query: $query_")
            }
        }

        state_.asAxiom().assertForm()
        query_.axiom.assertForm()

        val pred = ef.makeBool("$\$CHECK$$")
        pred.implies(query_).expr.assertForm()
        pred.expr.assertForm()
        log.debug("Running Boolector solver")
        val result = BoolectorSat.getBoolectorSat()
                ?: unreachable { log.error("Solver error") }//null?

        log.debug("Solver finished")

        return when (result) {
            BoolectorSat.Status.SAT -> {
                val model = "there is no model class in boolector" //?: unreachable { log.error("Solver result does not contain model") }
//               log.debug(model)
                result.toInt() to model
            }
            BoolectorSat.Status.UNSAT -> {
                val core = "unsatCore not added"//solver.unsatCore.toList()
                log.debug(core)
                result.toInt() to core
            }
            BoolectorSat.Status.UNKNOWN -> {
                val reason = "like reason not necessarily" //solver.reasonUnknown
                log.debug(reason)
                result.toInt() to reason
            }
        }
    }

    private fun collectModel(ctx: BoolectorContext, vararg states: PredicateState): SMTModel {
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.map {
            val expr = BoolectorConverter(tf).convert(it, ef, ctx)
            val boolectorExpr = expr.expr

            log.debug("Evaluating $boolectorExpr")
            it to BoolectorUnlogic.undo(boolectorExpr)
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

            require(assignments.getOrPut(ptr) { modelPtr } == modelPtr)
        }

        return SMTModel(assignments,
                memories.map { it.key to MemoryShape(it.value.first, it.value.second) }.toMap(),
                bounds.map { it.key to MemoryShape(it.value.first, it.value.second) }.toMap())
    }

    override fun cleanup() = ef.ctx.btorRelease()
}
