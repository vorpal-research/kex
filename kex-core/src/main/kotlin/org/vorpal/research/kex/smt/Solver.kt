package org.vorpal.research.kex.smt

import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import java.io.Closeable

sealed class Result {
    open val known: Boolean = true

    open fun match(other: Result) = false

    class SatResult(val model: SMTModel) : Result() {
        override fun toString() = "sat"

        override fun match(other: Result) = other is SatResult
    }

    class UnsatResult(val message: String = "unsat") : Result() {
        override fun toString() = message

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

@Suppress("unused")
@AbstractIncrementalSolver
interface AbstractIncrementalSMTSolver : AbstractSMTSolver, Closeable {
    fun isSatisfiable(
        state: PredicateState,
        query: PredicateQuery
    ): Result = isSatisfiable(state, listOf(query)).single()

    fun isSatisfiable(
        state: PredicateState,
        queries: List<PredicateQuery>
    ): List<Result>
}

@AbstractAsyncIncrementalSolver
interface AbstractAsyncIncrementalSMTSolver : AbstractAsyncSMTSolver, Closeable {
    suspend fun isSatisfiableAsync(
        state: PredicateState,
        query: PredicateQuery
    ): Result = isSatisfiableAsync(state, listOf(query)).single()

    suspend fun isSatisfiableAsync(
        state: PredicateState,
        queries: List<PredicateQuery>
    ): List<Result>
}
