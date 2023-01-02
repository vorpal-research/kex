package org.vorpal.research.kex.smt

import org.vorpal.research.kex.state.PredicateState
import java.io.Closeable

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
interface AbstractSMTSolver : Closeable {
    fun isReachable(state: PredicateState): Result
    fun isPathPossible(state: PredicateState, path: PredicateState): Result
    fun isViolated(state: PredicateState, query: PredicateState): Result
}

@AbstractAsyncSolver
interface AbstractAsyncSMTSolver : Closeable {
    suspend fun isReachableAsync(state: PredicateState): Result
    suspend fun isPathPossibleAsync(state: PredicateState, path: PredicateState): Result
    suspend fun isViolatedAsync(state: PredicateState, query: PredicateState): Result
}
