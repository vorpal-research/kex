package org.jetbrains.research.kex.smt.z3

import com.abdullin.kthelper.assert.ktassert
import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.debug
import com.abdullin.kthelper.logging.log
import com.microsoft.z3.*
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.smt.*
import org.jetbrains.research.kex.smt.Solver
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.collectPointers
import org.jetbrains.research.kex.state.transformer.collectVariables
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kfg.type.TypeFactory

private val timeout = kexConfig.getIntValue("smt", "timeout", 3) * 1000
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)
private val printSMTLib = kexConfig.getBooleanValue("smt", "logSMTLib", false)
private val simplifyFormulae = kexConfig.getBooleanValue("smt", "simplifyFormulae", false)

@Solver("z3")
class Z3Solver(val tf: TypeFactory) : AbstractSMTSolver {
    val ef = Z3ExprFactory()

    override fun isReachable(state: PredicateState) =
            isPathPossible(state, state.path)

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

        solver.add(state_.asAxiom() as BoolExpr)
        solver.add(ef.buildSubtypeAxioms(tf).asAxiom() as BoolExpr)
        solver.add(query_.axiom as BoolExpr)
        solver.add(query_.expr as BoolExpr)

        log.debug("Running z3 solver")
        if (printSMTLib) {
            log.debug("SMTLib formula:")
            log.debug(solver)
        }
        val result = solver.check(query.expr) ?: unreachable { log.error("Solver error") }
        log.debug("Solver finished")

        return when (result) {
            Status.SATISFIABLE -> {
                val model = solver.model ?: unreachable { log.error("Solver result does not contain model") }
                if (logFormulae) log.debug(model)
                result to model
            }
            Status.UNSATISFIABLE -> {
                val core = solver.unsatCore.toList()
                log.debug("Unsat core: $core")
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
        Z3Params.load().forEach { (name, value) ->
            Global.setParameter(name, value.toString())
        }

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
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.map {
            val expr = Z3Converter(tf).convert(it, ef, ctx)
            val z3expr = expr.expr

            val evaluatedExpr = model.evaluate(z3expr, true)
            it to Z3Unlogic.undo(evaluatedExpr)
        }.toMap().toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val properties = hashMapOf<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()

        for ((type, value) in ef.typeMap) {
            val actualValue = model.evaluate(value.expr, true)
            log.debug("Type $type = $actualValue")
        }

        for (ptr in ptrs) {
            val memspace = ptr.memspace

            when (ptr) {
                is FieldTerm -> {
                    val ptrExpr = Z3Converter(tf).convert(ptr.owner, ef, ctx) as? Ptr_
                            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

                    val name = "${ptr.klass}.${ptr.fieldNameString}"
                    val startProp = ctx.getInitialProperties(memspace, name)
                    val endProp = ctx.getProperties(memspace, name)

                    val startV = startProp.load(ptrExpr, Z3ExprFactory.getTypeSize((ptr.type as KexReference).reference).int)
                    val endV = endProp.load(ptrExpr, Z3ExprFactory.getTypeSize((ptr.type as KexReference).reference).int)

                    val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))
                    val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
                    val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))

                    val pair = properties.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
                        hashMapOf<Term, Term>() to hashMapOf()
                    }
                    pair.first[modelPtr] = modelStartV
                    pair.second[modelPtr] = modelEndV
                }
                else -> {
                    val startMem = ctx.getInitialMemory(memspace)
                    val endMem = ctx.getMemory(memspace)

                    val ptrExpr = Z3Converter(tf).convert(ptr, ef, ctx) as? Ptr_
                            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

                    val startV = startMem.load(ptrExpr, Z3ExprFactory.getTypeSize(ptr.type).int)
                    val endV = endMem.load(ptrExpr, Z3ExprFactory.getTypeSize(ptr.type).int)

                    val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))
                    val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
                    val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))

                    memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
                    memories.getValue(memspace).first[modelPtr] = modelStartV
                    memories.getValue(memspace).second[modelPtr] = modelEndV

                    val startTypes = ctx.getInitialProperties(memspace, "type")
                    val endTypes = ctx.getProperties(memspace, "type")

                    val startT = startTypes.load(ptrExpr, Z3ExprFactory.getTypeSize(ptr.type).int)
                    val endT = endTypes.load(ptrExpr, Z3ExprFactory.getTypeSize(ptr.type).int)

                    val modelStartT = Z3Unlogic.undo(model.evaluate(startT.expr, true))
                    val modelEndT = Z3Unlogic.undo(model.evaluate(endT.expr, true))

                    log.debug("Pointer $ptr type is $modelEndT")

                    if (ptr.type is KexArray) {
                        val startProp = ctx.getInitialProperties(memspace, "length")
                        val endProp = ctx.getProperties(memspace, "length")

                        val startLength = startProp.load(ptrExpr, Z3ExprFactory.getTypeSize(KexInt()).int)
                        val endLength = endProp.load(ptrExpr, Z3ExprFactory.getTypeSize(KexInt()).int)

                        val startLengthV = Z3Unlogic.undo(model.evaluate(startLength.expr, true))
                        val endLengthV = Z3Unlogic.undo(model.evaluate(endLength.expr, true))

                        val pair = properties.getOrPut(memspace, ::hashMapOf).getOrPut("length") {
                            hashMapOf<Term, Term>() to hashMapOf()
                        }
                        pair.first[modelPtr] = startLengthV
                        pair.second[modelPtr] = endLengthV
                    }

                    ktassert(assignments.getOrPut(ptr) { modelPtr } == modelPtr)
                }
            }
        }
        return SMTModel(
                assignments,
                memories.map { (memspace, pair) -> memspace to MemoryShape(pair.first, pair.second) }.toMap(),
                properties.map { (memspace, names) ->
                    memspace to names.map { (name, pair) -> name to MemoryShape(pair.first, pair.second) }.toMap()
                }.toMap()
        )
    }

    override fun cleanup() {
        ef.ctx.close()
    }
}