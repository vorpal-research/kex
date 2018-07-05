package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.castTo
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.unreachable

private val timeout = GlobalConfig.getIntValue("smt.timeout", 3)

class Z3Solver(val ef: Z3ExprFactory) : Loggable {

    sealed class Result(val status: Status) {
        class SatResult(status: Status, val model: Model) : Result(status)
        class UnsatResult(status: Status, val core: List<Expr>) : Result(status)
        class UnknownResult(status: Status, val reason: String) : Result(status)
    }

    fun check(state: Bool_, query: Bool_, ctx: Z3Context): Result {
        val solver = tactic().solver ?: unreachable { log.error("Can't create solver") }

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
        log.debug("Solver finished")
        log.debug("Acquired result: $result")
        log.debug("With:")

        return when (result) {
            Status.SATISFIABLE -> {
                val model = solver.model ?: unreachable { log.error("Solver result does not contain model")}
                log.debug(model)
                Result.SatResult(result, model)
            }
            Status.UNSATISFIABLE -> {
                val core = solver.unsatCore.toList()
                log.debug(core)
                Result.UnsatResult(result, core)
            }
            Status.UNKNOWN -> {
                val reason = solver.reasonUnknown
                log.debug(reason)
                Result.UnknownResult(result, reason)
            }
        }
    }

    fun tactic(): Tactic {
        val ctx = ef.ctx
        val params = ctx.mkParams()
        params.add("elim_and", true);
        params.add("sort_store", true);
        val tactic = ctx.mkTactic("solverTactic")
        return ctx.with(tactic, params)
    }
}