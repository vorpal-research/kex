package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.smt.AbstractSMTSolver
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.util.*

private val timeout = GlobalConfig.getIntValue("smt", "timeout", 3)

class Z3Solver(val ef: Z3ExprFactory) : AbstractSMTSolver {

    override fun isReachable(state: PredicateState) =
            isPathPossible(state, state.filterByType(PredicateType.Path()))

    override fun isPathPossible(state: PredicateState, path: PredicateState) = isViolated(state, path)

    override fun isViolated(state: PredicateState, query: PredicateState): Result {
        log.run {
            debug("Z3 solver check")
            debug("State: $state")
            debug("Query: $query")
        }

        val ctx = Z3Context(ef, 0, 0)

        val z3State = Z3Converter.convert(state, ef, ctx)
        val z3query = Z3Converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(z3State, z3query, ctx)
        log.debug("Check finished")
        return result
    }

    private fun check(state: Bool_, query: Bool_, ctx: Z3Context): Result {
        val solver = buildTactics().solver ?: unreachable { log.error("Can't create solver") }

        val state_ = state.simplify()
        val query_ = query.simplify()

        log.run {
            debug("State: $state_")
            debug("Query: $query_")
        }

        solver.add(state_.asAxiom().castTo())
        solver.add(query_.axiom.castTo())

        val pred = ef.makeBool("$\$CHECK$$")
        solver.add(pred.implies(query_).expr.castTo())

        log.debug("Running z3 solver")
        val result = solver.check(pred.expr) ?: unreachable { log.error("Solver error") }
        log.run {
            debug("Solver finished")
            debug("Acquired result: $result")
            debug("With:")
        }

        return when (result) {
            Status.SATISFIABLE -> {
                val model = solver.model ?: unreachable { log.error("Solver result does not contain model") }
                log.debug(model)
                TODO()
//                Result.SatResult(collectModel(sta))
            }
            Status.UNSATISFIABLE -> {
                val core = solver.unsatCore.toList()
                log.debug(core)
                Result.UnsatResult()
            }
            Status.UNKNOWN -> {
                val reason = solver.reasonUnknown
                log.debug(reason)
                Result.UnknownResult(reason)
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

    private fun collectModel(state: PredicateState, ctx: Z3Context, model: Model): SMTModel = TODO()
}