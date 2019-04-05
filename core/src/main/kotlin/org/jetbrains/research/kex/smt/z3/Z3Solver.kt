package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.Model
import com.microsoft.z3.Status
import com.microsoft.z3.Tactic
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.smt.AbstractSMTSolver
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.MemoryShape
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.PointerCollector
import org.jetbrains.research.kex.state.transformer.VariableCollector
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.castTo
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.TypeFactory

private val timeout = GlobalConfig.getIntValue("smt", "timeout", 3) * 1000

private val logQuery = GlobalConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = GlobalConfig.getBooleanValue("smt", "logFormulae", false)
private val simplifyFormulae = GlobalConfig.getBooleanValue("smt", "simplifyFormulae", false)

class Z3Solver(val tf: TypeFactory) : AbstractSMTSolver {
    val ef = Z3ExprFactory()

    override fun isReachable(state: PredicateState) =
            isPathPossible(state, state.filterByType(PredicateType.Path()))

    override fun isPathPossible(state: PredicateState, path: PredicateState) = isViolated(state, path)

    override fun isViolated(state: PredicateState, query: PredicateState): Result {
        if (logQuery) {
            log.run {
                debug("Z3 solver check")
                debug("State: $state")
                debug("Query: $query")
            }
        }

        val ctx = Z3Context(ef, (1 shl 8) + 1, (1 shl 24) + 1)

        val converter = Z3Converter(tf)
        val z3State = converter.convert(state, ef, ctx)
        val z3query = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(z3State, z3query)
        log.debug("Check finished")
        return when (result.first) {
            Status.UNSATISFIABLE -> Result.UnsatResult
            Status.UNKNOWN -> Result.UnknownResult(result.second as String)
            Status.SATISFIABLE -> Result.SatResult(collectModel(ctx, result.second as Model, state))
        }
    }

    private fun check(state: Bool_, query: Bool_): Pair<Status, Any> {
        val solver = buildTactics().solver ?: unreachable { log.error("Can't create solver") }

        val (state_, query_) = when {
            simplifyFormulae -> state.simplify() to query.simplify()
            else -> state to query
        }

        if (logFormulae) {
            log.run {
                debug("State: $state_")
                debug("Query: $query_")
            }
        }

        solver.add(state_.asAxiom().castTo())
        solver.add(query_.axiom.castTo())

        val pred = ef.makeBool("$\$CHECK$$")
        solver.add(pred.implies(query_).expr.castTo())

        log.debug("Running z3 solver")
        val result = solver.check(pred.expr) ?: unreachable { log.error("Solver error") }
        log.debug("Solver finished")

        return when (result) {
            Status.SATISFIABLE -> {
                val model = solver.model ?: unreachable { log.error("Solver result does not contain model") }
//                log.debug(model)
                result to model
            }
            Status.UNSATISFIABLE -> {
                val core = solver.unsatCore.toList()
                log.debug(core)
                result to core
            }
            Status.UNKNOWN -> {
                val reason = solver.reasonUnknown
                log.debug(reason)
                result to reason
            }
        }
    }

    private fun buildTactics(): Tactic {
        val ctx = ef.ctx
        val tactic = Z3Tactics.load().map {
            val tactic = ctx.mkTactic(it.type)
            val params = ctx.mkParams()
            it.params.forEach { (name, value) ->
                when (value) {
                    is Value.BoolValue -> params.add(name, value.value)
                    is Value.IntValue -> params.add(name, value.value)
                    is Value.DoubleValue -> params.add(name, value.value)
                    is Value.StringValue -> params.add(name, value.value)
                }
            }
            ctx.with(tactic, params)
        }.reduce { a, b -> ctx.andThen(a, b) }
        return ctx.tryFor(tactic, timeout)
    }

    private fun collectModel(ctx: Z3Context, model: Model, vararg states: PredicateState): SMTModel {
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + PointerCollector(ps) to acc.second + VariableCollector(ps)
        }

        val assignments = vars.map {
            val expr = Z3Converter(tf).convert(it, ef, ctx)
            val z3expr = expr.expr

            log.debug("Evaluating $z3expr")
            val evaluatedExpr = model.evaluate(z3expr, true)
            it to Z3Unlogic.undo(evaluatedExpr)
        }.toMap().toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val bounds = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()

        for (ptr in ptrs) {
            val memspace = ptr.memspace

            val startMem = ctx.getInitialMemory(memspace)
            val endMem = ctx.getMemory(memspace)

            val startBounds = ctx.getBounds(memspace)
            val endBounds = ctx.getBounds(memspace)

            val eptr = Z3Converter(tf).convert(ptr, ef, ctx) as? Ptr_
                    ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

            val startV = startMem.load(eptr, Z3ExprFactory.getTypeSize(ptr.type))
            val endV = endMem.load(eptr, Z3ExprFactory.getTypeSize(ptr.type))

            val startB = startBounds[eptr]
            val endB = endBounds[eptr]


            val modelPtr = Z3Unlogic.undo(model.evaluate(eptr.expr, true))
            val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
            val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
            val modelStartB = Z3Unlogic.undo(model.evaluate(startB.expr, true))
            val modelEndB = Z3Unlogic.undo(model.evaluate(endB.expr, true))

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

    override fun cleanup() {
        ef.ctx.close()
    }
}