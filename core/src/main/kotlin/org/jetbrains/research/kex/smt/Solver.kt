package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.smt.z3.Z3Solver
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.TypeFactory

sealed class Result {
    open val known: Boolean = true

    open fun match(other: Result) = false

    class SatResult(val model: SMTModel) : Result() {
        override fun toString() = "sat"

        override fun match(other: Result) = other is SatResult
    }

    object UnsatResult : Result() {
        override fun toString() = "unsat"

        override fun match(other: Result) = other is UnsatResult
    }

    class UnknownResult(val reason: String) : Result() {
        override val known: Boolean
            get() = false

        override fun toString() = "unknown"

        override fun match(other: Result) = other is UnknownResult
    }
}

interface AbstractSMTSolver {
    fun isReachable(state: PredicateState): Result
    fun isPathPossible(state: PredicateState, path: PredicateState): Result
    fun isViolated(state: PredicateState, query: PredicateState): Result

    fun cleanup()
}

val engine = GlobalConfig.getStringValue("smt", "engine")
        ?: unreachable { log.error("No SMT engine specified") }

class SMTProxySolver(
        tf: TypeFactory,
        val solver: AbstractSMTSolver = when (engine) {
            "z3" -> Z3Solver(tf)
            else -> unreachable { log.error("Unknown smt engine: $engine") }
        }) : AbstractSMTSolver by solver
