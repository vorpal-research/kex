package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.state.PredicateState

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

@AbstractSolver
interface AbstractSMTSolver {
    fun isReachable(state: PredicateState): Result
    fun isPathPossible(state: PredicateState, path: PredicateState): Result
    fun isViolated(state: PredicateState, query: PredicateState): Result

    fun cleanup()
}

