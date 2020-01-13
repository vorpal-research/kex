package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.state.BaseType
import org.jetbrains.research.kex.state.InheritanceInfo
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.util.fail
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

@BaseType("Solver")
interface AbstractSMTSolver {
    fun isReachable(state: PredicateState): Result
    fun isPathPossible(state: PredicateState, path: PredicateState): Result
    fun isViolated(state: PredicateState, query: PredicateState): Result

    fun cleanup()
}

private val engine = kexConfig.getStringValue("smt", "engine")
        ?: unreachable { log.error("No SMT engine specified") }

class SMTProxySolver(
        tf: TypeFactory,
        val solver: AbstractSMTSolver = getSolver(tf, engine)
) : AbstractSMTSolver by solver {

    companion object {
        val solvers = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("AbstractSMTSolver.json")
                    ?: fail { log.error("Could not load smt solver inheritance info") }
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo?.inheritors?.map {
                it.name to loader.loadClass(it.inheritorClass)
            }?.toMap() ?: mapOf()
        }

        fun getSolver(tf: TypeFactory, engine: String): AbstractSMTSolver {
            val solverClass = solvers[engine] ?: unreachable { log.error("Unknown engine name: $engine") }
            val constructor = solverClass.getConstructor(TypeFactory::class.java)
            return constructor.newInstance(tf) as AbstractSMTSolver
        }
    }

    constructor(tf: TypeFactory, engine: String) : this(tf, getSolver(tf, engine))
}
