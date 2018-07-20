package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.smt.AbstractSMTSolver
import org.jetbrains.research.kex.smt.MemoryShape
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Memspaced
import org.jetbrains.research.kex.state.transformer.PointerCollector
import org.jetbrains.research.kex.state.transformer.VariableCollector
import org.jetbrains.research.kex.util.*

private val timeout = GlobalConfig.getIntValue("smt", "timeout", 3) * 1000

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

        val ctx = Z3Context(ef, (1 shl 8) + 1, (1 shl 24) + 1)

        val z3State = Z3Converter.convert(state, ef, ctx)
        val z3query = Z3Converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(z3State, z3query)
        log.debug("Check finished")
        return when (result.first) {
            Status.UNSATISFIABLE -> Result.UnsatResult()
            Status.UNKNOWN -> Result.UnknownResult(result.second as String)
            Status.SATISFIABLE -> Result.SatResult(collectModel(ctx, result.second as Model, state))
        }
    }

    private fun check(state: Bool_, query: Bool_): Pair<Status, Any> {
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
        val ptrCollector = PointerCollector()
        val varCollector = VariableCollector()
        states.forEach {
            ptrCollector.transform(it)
            varCollector.transform(it)
        }

        val ptrs = ptrCollector.ptrs
        val vars = varCollector.variables

        val assignments = vars.map {
            val expr = Z3Converter.convert(it, ef, ctx)
            val z3expr = expr.expr

            log.debug("Evaluating $z3expr")
            val evaluatedExpr = model.evaluate(z3expr, true)
            it to Z3Unlogic.undo(evaluatedExpr)
        }.toMap()

        val memories = mutableMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val bounds =  mutableMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()

        ptrs.forEach { ptr ->
            val memspace = (ptr.type as? Memspaced<*>)?.memspace ?: 0

            val startMem = ctx.getInitialMemory(memspace)
            val endMem = ctx.getMemory(memspace)

            val startBounds = ctx.getBounds(memspace)
            val endBounds = ctx.getBounds(memspace)

            val eptr = Z3Converter.convert(ptr, ef, ctx) as? Ptr_ ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

            val startV = startMem.load(eptr, Z3ExprFactory.getTypeSize(ptr.type))
            val endV = endMem.load(eptr, Z3ExprFactory.getTypeSize(ptr.type))

            val startB = startBounds[eptr]
            val endB = endBounds[eptr]


            val modelPtr = Z3Unlogic.undo(model.evaluate(eptr.expr, true))
            val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
            val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
            val modelStartB = Z3Unlogic.undo(model.evaluate(startB.expr, true))
            val modelEndB = Z3Unlogic.undo(model.evaluate(endB.expr, true))

            memories.getOrPut(memspace) { mutableMapOf<Term, Term>() to mutableMapOf<Term, Term>() }
            memories.getValue(memspace).first[modelPtr] = modelStartV
            memories.getValue(memspace).second[modelPtr] = modelEndV

            bounds.getOrPut(memspace) { mutableMapOf<Term, Term>() to mutableMapOf<Term, Term>() }
            bounds.getValue(memspace).first[modelPtr] = modelStartB
            bounds.getValue(memspace).second[modelPtr] = modelEndB
        }

        return SMTModel(assignments,
                memories.map { it.key to MemoryShape(it.value.first, it.value.second) }.toMap(),
                bounds.map { it.key to MemoryShape(it.value.first, it.value.second) }.toMap())
    }
}