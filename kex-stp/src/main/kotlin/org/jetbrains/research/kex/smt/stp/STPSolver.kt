package org.jetbrains.research.kex.smt.stp

import com.abdullin.kthelper.assert.ktassert
import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.smt.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.collectPointers
import org.jetbrains.research.kex.state.transformer.collectVariables
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kfg.type.TypeFactory
import org.zhekehz.stpjava.QueryResult

private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)

@Solver("stp")
class STPSolver(val tf: TypeFactory) : AbstractSMTSolver {
    val ef = STPExprFactory()

    override fun isReachable(state: PredicateState) =
            isPathPossible(state, state.path)

    override fun isPathPossible(state: PredicateState, path: PredicateState) = isViolated(state, path)

    override fun isViolated(state: PredicateState, query: PredicateState): Result {
        if (logQuery) {
            log.run {
                debug("STP solver check")
                debug("State: $state")
                debug("Query: $query")
            }
        }

        val ctx = STPContext(ef, (1 shl 8) + 1, (1 shl 24) + 1)

        val converter = STPConverter(tf)
        val STPState = converter.apply(state, ef, ctx)
        val STPQuery = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(STPState, STPQuery)
        log.debug("Check finished")
        return when (result) {
            QueryResult.INVALID -> Result.SatResult(collectModel(ctx, state))
            QueryResult.TIMEOUT, QueryResult.ERROR -> Result.UnknownResult("should not happen")
            QueryResult.VALID -> Result.UnsatResult
        }
    }

    private fun check(state: Bool_, query: Bool_): QueryResult {
        val subTypeAxioms = ef.buildSubtypeAxioms(tf)
        val combined = state and query and subTypeAxioms
        val toBeChecked = combined.expr.asBool().and(combined.axiom.asBool())

        if (logFormulae) {
            log.debug(toBeChecked.toSMTLib2())
        }
        log.debug("Running STP solver")
        val result = toBeChecked.not().query() ?: unreachable { log.error("Solver error") }
        log.debug("Solver finished")

        return result
    }

    private fun STPContext.recoverProperty(ptr: Term, memspace: Int, type: KexType, name: String): Pair<Term, Term> {
        val ptrExpr = STPConverter(tf).convert(ptr, ef, this) as? Ptr_
                ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val startProp = getInitialProperties(memspace, name)
        val endProp = getProperties(memspace, name)

        val startV = startProp.load(ptrExpr, STPExprFactory.getTypeSize(type).int)
        val endV = endProp.load(ptrExpr, STPExprFactory.getTypeSize(type).int)

        val modelStartV = STPUnlogic.undo(startV.expr)
        val modelEndV = STPUnlogic.undo(endV.expr)
        return modelStartV to modelEndV
    }

    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverProperty(
            ctx: STPContext,
            ptr: Term,
            memspace: Int,
            type: KexType,
            name: String
    ) {
        val ptrExpr = STPConverter(tf).convert(ptr, ef, ctx) as? Ptr_
                ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = STPUnlogic.undo(ptrExpr.expr)

        val (modelStartT, modelEndT) = ctx.recoverProperty(ptr, memspace, type, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
    }

    private fun collectModel(ctx: STPContext, vararg states: PredicateState): SMTModel {
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.map {
            val expr = STPConverter(tf).convert(it, ef, ctx)
            val stpExpr = expr.expr

            val undone = STPUnlogic.undo(stpExpr)
            val actualValue = when {
                it.type is KexReal -> when (undone) {
                    is ConstIntTerm -> term { const(undone.value.toFloat()) }
                    is ConstLongTerm -> term { const(undone.value.toDouble()) }
                    else -> unreachable {
                        log.error("Unexpected integral term when trying to reanimate floating point value: $undone")
                    }
                }
                else -> undone
            }
            it to actualValue
        }.toMap().toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val properties = hashMapOf<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val typeMap = hashMapOf<Term, KexType>()

        for ((type, value) in ef.typeMap) {
            val actualValue = STPUnlogic.undo(value.expr)
            typeMap[actualValue] = type
        }

        for (ptr in ptrs) {
            val memspace = ptr.memspace

            when (ptr) {
                is FieldLoadTerm -> {}
                is FieldTerm -> {
                    val name = "${ptr.klass}.${ptr.fieldNameString}"
                    properties.recoverProperty(ctx, ptr.owner, memspace, (ptr.type as KexReference).reference, name)
                    properties.recoverProperty(ctx, ptr.owner, memspace, ptr.type, "type")
                }
                else -> {
                    val startMem = ctx.getInitialMemory(memspace)
                    val endMem = ctx.getMemory(memspace)

                    val ptrExpr = STPConverter(tf).convert(ptr, ef, ctx) as? Ptr_
                            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

                    val startV = startMem.load(ptrExpr, STPExprFactory.getTypeSize(ptr.type).int)
                    val endV = endMem.load(ptrExpr, STPExprFactory.getTypeSize(ptr.type).int)

                    val modelPtr = STPUnlogic.undo(ptrExpr.expr)
                    val modelStartV = STPUnlogic.undo(startV.expr)
                    val modelEndV = STPUnlogic.undo(endV.expr)

                    memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
                    memories.getValue(memspace).first[modelPtr] = modelStartV
                    memories.getValue(memspace).second[modelPtr] = modelEndV

                    properties.recoverProperty(ctx, ptr, memspace, ptr.type, "type")

                    if (ptr.type is KexArray) {
                        properties.recoverProperty(ctx, ptr, memspace, KexInt(), "length")
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
                }.toMap(),
                typeMap
        )
    }

    override fun cleanup() = ef.ctx.destroy()
}
