package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.smt.z3.Z3ExprFactory
import org.jetbrains.research.kex.smt.z3.Z3Solver
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable

sealed class Result {
    class SatResult(val model: SMTModel) : Result() {
        override fun toString() = "sat"
    }

    class UnsatResult : Result() {
        override fun toString() = "unsat"
    }

    class UnknownResult(val reason: String) : Result() {
        override fun toString() = "unknown"
    }
}

interface AbstractSMTSolver {
    fun isReachable(state: PredicateState): Result
    fun isPathPossible(state: PredicateState, path: PredicateState): Result
    fun isViolated(state: PredicateState, query: PredicateState): Result
}

val engine = GlobalConfig.getStringValue("smt", "engine")
        ?: unreachable { log.error("No SMT engine specified") }

class SMTProxySolver(
        val solver: AbstractSMTSolver = when (engine) {
            "z3" -> Z3Solver(Z3ExprFactory())
            else -> unreachable { log.error("Unknown smt engine: $engine") }
        }) : AbstractSMTSolver by solver
