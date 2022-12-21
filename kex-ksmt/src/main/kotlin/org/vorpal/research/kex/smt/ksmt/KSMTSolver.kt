@file:OptIn(ExperimentalTime::class)

package org.vorpal.research.kex.smt.ksmt

import org.ksmt.expr.KExpr
import org.ksmt.runner.core.*
import org.ksmt.solver.KModel
import org.ksmt.solver.KSolver
import org.ksmt.solver.KSolverStatus
import org.ksmt.solver.runner.KSolverRunnerManager
import org.ksmt.solver.z3.KZ3Solver
import org.ksmt.sort.KBoolSort
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.smt.AbstractSMTSolver
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.smt.Solver
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private val timeout = kexConfig.getIntValue("smt", "timeout", 3) * 1000
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)
private val printSMTLib = kexConfig.getBooleanValue("smt", "logSMTLib", false)
private val simplifyFormulae = kexConfig.getBooleanValue("smt", "simplifyFormulae", false)
private val maxArrayLength = kexConfig.getIntValue("smt", "maxArrayLength", 1000)

@Suppress("UNCHECKED_CAST")
@Solver("ksmt")
class KSMTSolver(val tf: TypeFactory) : AbstractSMTSolver {
    companion object {
        private val solverManager: KSolverRunnerManager by lazy {
            KSolverRunnerManager(
                workerPoolSize = 1,
                hardTimeout = timeout.milliseconds,
                workerProcessIdleTimeout = 10.seconds,
            )
        }
    }

    val ef = KSMTExprFactory()

    override fun isReachable(state: PredicateState) =
        isPathPossible(state, state.path)

    override fun isPathPossible(state: PredicateState, path: PredicateState): Result = check(state, path) { it }


    override fun isViolated(state: PredicateState, query: PredicateState): Result = check(state, query) { !it }

    fun check(state: PredicateState, query: PredicateState, queryBuilder: (Bool_) -> Bool_): Result {
        if (logQuery) {
            log.run {
                debug("KSMT solver check")
                debug("State: $state")
                debug("Query: $query")
            }
        }

        val ctx = KSMTContext(ef)

        val converter = KSMTConverter(tf)
        converter.init(state, ef)
        val ksmtState = converter.convert(state, ef, ctx)
        val ksmtQuery = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(ksmtState, queryBuilder(ksmtQuery))
        log.debug("Check finished")
        return when (result.first) {
            KSolverStatus.UNSAT -> Result.UnsatResult
            KSolverStatus.UNKNOWN -> Result.UnknownResult(result.second as String)
            KSolverStatus.SAT -> Result.SatResult(collectModel(ctx, result.second as KModel, state))
        }
    }

    private fun check(state: Bool_, query: Bool_): Pair<KSolverStatus, Any> {
        val solver = buildSolver()

        if (logFormulae) {
            log.run {
                debug("State: $state")
                debug("Query: $query")
            }
        }

        solver.assert(state.asAxiom() as KExpr<KBoolSort>)
        solver.assert(ef.buildConstClassAxioms().asAxiom() as KExpr<KBoolSort>)
        solver.assert(query.axiom as KExpr<KBoolSort>)
        solver.assert(query.expr as KExpr<KBoolSort>)

        log.debug("Running KSMT solver")
        if (printSMTLib) {
            log.debug("SMTLib formula:")
            log.debug(solver.toString())
        }
        val result = solver.check(timeout.milliseconds)
        log.debug("Solver finished")

        return when (result) {
            KSolverStatus.SAT -> {
                val model = solver.model()
                if (logFormulae) log.debug(model)
                result to model
            }
            KSolverStatus.UNSAT -> {
                val core = solver.unsatCore()
                log.debug("Unsat core: $core")
                result to core
            }
            KSolverStatus.UNKNOWN -> {
                val reason = solver.reasonOfUnknown()
                log.debug(reason)
                result to reason
            }
        }
    }

    private fun buildSolver(): KSolver<*> {
        return solverManager.createSolver(ef.ctx, KZ3Solver::class)
    }

        private fun KSMTContext.recoverBitvectorProperty(
        ptr: Term,
        memspace: Int,
        model: KModel,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = KSMTConverter(tf).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val startProp = getBitvectorInitialProperty(memspace, name)
        val endProp = getBitvectorProperty(memspace, name)

        val startV = startProp.load(ptrExpr)
        val endV = endProp.load(ptrExpr)

//        val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
//        val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
//        return modelStartV to modelEndV
            TODO()
    }

//    private fun Z3Context.recoverProperty(
//        ptr: Term,
//        memspace: Int,
//        type: KexType,
//        model: Model,
//        name: String
//    ): Pair<Term, Term> {
//        val ptrExpr = Z3Converter(tf).convert(ptr, ef, this) as? Ptr_
//            ?: unreachable { org.vorpal.research.kthelper.logging.log.error("Non-ptr expr for pointer $ptr") }
//        val typeSize = Z3ExprFactory.getTypeSize(type)
//        val startProp = when (typeSize) {
//            TypeSize.WORD -> getWordInitialProperty(memspace, name)
//            TypeSize.DWORD -> getDWordInitialProperty(memspace, name)
//        }
//        val endProp = when (typeSize) {
//            TypeSize.WORD -> getWordProperty(memspace, name)
//            TypeSize.DWORD -> getDWordProperty(memspace, name)
//        }
//
//        val startV = startProp.load(ptrExpr)
//        val endV = endProp.load(ptrExpr)
//
//        val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
//        val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
//        return modelStartV to modelEndV
//    }
//
//    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverProperty(
//        ctx: Z3Context,
//        ptr: Term,
//        memspace: Int,
//        type: KexType,
//        model: Model,
//        name: String
//    ): Pair<Term, Term> {
//        val ptrExpr = Z3Converter(tf).convert(ptr, ef, ctx) as? Ptr_
//            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
//        val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))
//
//        val (modelStartT, modelEndT) = ctx.recoverProperty(ptr, memspace, type, model, name)
//        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
//            hashMapOf<Term, Term>() to hashMapOf()
//        }
//        typePair.first[modelPtr] = modelStartT
//        typePair.second[modelPtr] = modelEndT
//        return modelStartT to modelEndT
//    }
//
//    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverBitvectorProperty(
//        ctx: Z3Context,
//        ptr: Term,
//        memspace: Int,
//        model: Model,
//        name: String
//    ): Pair<Term, Term> {
//        val ptrExpr = Z3Converter(tf).convert(ptr, ef, ctx) as? Ptr_
//            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
//        val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))
//
//        val (modelStartT, modelEndT) = ctx.recoverBitvectorProperty(ptr, memspace, model, name)
//        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
//            hashMapOf<Term, Term>() to hashMapOf()
//        }
//        typePair.first[modelPtr] = modelStartT
//        typePair.second[modelPtr] = modelEndT
//        return modelStartT to modelEndT
//    }
//
    private fun collectModel(ctx: KSMTContext, model: KModel, vararg states: PredicateState): SMTModel {
//        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
//            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
//        }
//
//        val assignments = vars.associateWith {
//            val expr = Z3Converter(tf).convert(it, ef, ctx)
//            val z3expr = expr.expr
//
//            val evaluatedExpr = model.evaluate(z3expr, true)
//            Z3Unlogic.undo(evaluatedExpr)
//        }.toMutableMap()
//
//        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
//        val properties = hashMapOf<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
//        val arrays = hashMapOf<Int, MutableMap<Term, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
//        val typeMap = hashMapOf<Term, KexType>()
//
//        for ((type, value) in ef.typeMap) {
//            val actualValue = Z3Unlogic.undo(value.expr)
//            val index = when (actualValue) {
//                is ConstStringTerm -> term { const(actualValue.value.indexOf('1')) }
//                else -> term { const(log2(actualValue.numericValue.toDouble()).toInt()) }
//            }
//            typeMap[index] = type.kexType
//        }
//
//        val indices = hashSetOf<Term>()
//        for (ptr in ptrs) {
//            val memspace = ptr.memspace
//
//            when (ptr) {
//                is ArrayLoadTerm -> {}
//                is ArrayIndexTerm -> {
//                    val arrayPtrExpr = Z3Converter(tf).convert(ptr.arrayRef, ef, ctx) as? Ptr_
//                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
//                    val indexExpr = Z3Converter(tf).convert(ptr.index, ef, ctx) as? Int_
//                        ?: unreachable { log.error("Non integer expr for index in $ptr") }
//
//                    val modelPtr = Z3Unlogic.undo(model.evaluate(arrayPtrExpr.expr, true))
//                    val modelIndex = Z3Unlogic.undo(model.evaluate(indexExpr.expr, true))
//
//                    val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, ptr.arrayRef.memspace)
//                    val modelArray = ctx.readArrayMemory(arrayPtrExpr, ptr.arrayRef.memspace)
//
//                    val cast = { arrayVal: DWord_ ->
//                        when (Z3ExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
//                            TypeSize.WORD -> Word_.forceCast(arrayVal)
//                            TypeSize.DWORD -> arrayVal
//                        }
//                    }
//                    val initialValue = Z3Unlogic.undo(
//                        model.evaluate(
//                            cast(
//                                DWord_.forceCast(modelStartArray.load(indexExpr))
//                            ).expr,
//                            true
//                        )
//                    )
//                    val value = Z3Unlogic.undo(
//                        model.evaluate(
//                            cast(
//                                DWord_.forceCast(modelArray.load(indexExpr))
//                            ).expr,
//                            true
//                        )
//                    )
//
//                    val arrayPair = arrays.getOrPut(ptr.arrayRef.memspace, ::hashMapOf).getOrPut(modelPtr) {
//                        hashMapOf<Term, Term>() to hashMapOf()
//                    }
//                    arrayPair.first[modelIndex] = initialValue
//                    arrayPair.second[modelIndex] = value
//                }
//                is FieldLoadTerm -> {}
//                is FieldTerm -> {
//                    val name = "${ptr.klass}.${ptr.fieldName}"
//                    properties.recoverProperty(
//                        ctx,
//                        ptr.owner,
//                        memspace,
//                        (ptr.type as KexReference).reference,
//                        model,
//                        name
//                    )
//                    properties.recoverBitvectorProperty(ctx, ptr.owner, memspace, model, "type")
//                }
//                else -> {
//                    val startMem = ctx.getWordInitialMemory(memspace)
//                    val endMem = ctx.getWordMemory(memspace)
//
//                    val ptrExpr = Z3Converter(tf).convert(ptr, ef, ctx) as? Ptr_
//                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
//
//                    val startV = startMem.load(ptrExpr)
//                    val endV = endMem.load(ptrExpr)
//
//                    val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))
//                    val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
//                    val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
//
//                    memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
//                    memories.getValue(memspace).first[modelPtr] = modelStartV
//                    memories.getValue(memspace).second[modelPtr] = modelEndV
//                    if (ptr.type.isString) {
////                        val modelStartStr = ctx.readInitialStringMemory(ptrExpr, memspace)
////                        val modelStr = ctx.readStringMemory(ptrExpr, memspace)
////                        val startStringVal = Z3Unlogic.undo(model.evaluate(modelStartStr.expr, true))
////                        val stringVal = Z3Unlogic.undo(model.evaluate(modelStr.expr, true))
////                        strings.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
////                        strings.getValue(memspace).first[modelPtr] = startStringVal
////                        strings.getValue(memspace).second[modelPtr] = stringVal
//                    } else if (ptr.type.isArray) {
//                        val (_, endLength) = properties.recoverProperty(
//                            ctx,
//                            ptr,
//                            memspace,
//                            KexInt(),
//                            model,
//                            "length"
//                        )
//                        var maxLen = endLength.numericValue.toInt()
//                        // this is fucked up
//                        if (maxLen > maxArrayLength) {
//                            log.warn("Reanimated length of an array is too big: $maxLen")
//                            maxLen = maxArrayLength
//                        }
//                        properties[memspace]!!["length"]!!.first[modelPtr] = term { const(maxLen) }
//                        properties[memspace]!!["length"]!!.second[modelPtr] = term { const(maxLen) }
//                        for (i in 0 until maxLen) {
//                            val indexTerm = term { ptr[i] }
//                            if (indexTerm !in ptrs)
//                                indices += indexTerm
//                        }
//                    } else if (ptr is ConstClassTerm || ptr is ClassAccessTerm) {
//                        properties.recoverBitvectorProperty(ctx, ptr, memspace, model, ConstClassTerm.TYPE_INDEX_PROPERTY)
//                    }
//
//                    properties.recoverBitvectorProperty(ctx, ptr, memspace, model, "type")
//
//                    ktassert(assignments.getOrPut(ptr) { modelPtr } == modelPtr)
//                }
//            }
//        }
//        for (ptr in indices) {
//            ptr as ArrayIndexTerm
//            val memspace = ptr.arrayRef.memspace
//            val arrayPtrExpr = Z3Converter(tf).convert(ptr.arrayRef, ef, ctx) as? Ptr_
//                ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
//            val indexExpr = Z3Converter(tf).convert(ptr.index, ef, ctx) as? Int_
//                ?: unreachable { log.error("Non integer expr for index in $ptr") }
//
//            val modelPtr = Z3Unlogic.undo(model.evaluate(arrayPtrExpr.expr, true))
//            val modelIndex = Z3Unlogic.undo(model.evaluate(indexExpr.expr, true))
//
//            val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, memspace)
//            val modelArray = ctx.readArrayMemory(arrayPtrExpr, memspace)
//
//            val cast = { arrayVal: DWord_ ->
//                when (Z3ExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
//                    TypeSize.WORD -> Word_.forceCast(arrayVal)
//                    TypeSize.DWORD -> arrayVal
//                }
//            }
//            val initialValue = Z3Unlogic.undo(
//                model.evaluate(
//                    cast(
//                        DWord_.forceCast(modelStartArray.load(indexExpr))
//                    ).expr,
//                    true
//                )
//            )
//            val value = Z3Unlogic.undo(
//                model.evaluate(
//                    cast(
//                        DWord_.forceCast(modelArray.load(indexExpr))
//                    ).expr,
//                    true
//                )
//            )
//
//            val arrayPair = arrays.getOrPut(memspace, ::hashMapOf).getOrPut(modelPtr) {
//                hashMapOf<Term, Term>() to hashMapOf()
//            }
//            arrayPair.first[modelIndex] = initialValue
//            arrayPair.second[modelIndex] = value
//        }
//        return SMTModel(
//            assignments,
//            memories.map { (memspace, pair) -> memspace to MemoryShape(pair.first, pair.second) }.toMap(),
//            properties.map { (memspace, names) ->
//                memspace to names.map { (name, pair) -> name to MemoryShape(pair.first, pair.second) }.toMap()
//            }.toMap(),
//            arrays.map { (memspace, values) ->
//                memspace to values.map { (addr, pair) -> addr to MemoryShape(pair.first, pair.second) }.toMap()
//            }.toMap(),
//            typeMap
//        )
        return TODO()
    }

    override fun close() {
        ef.ctx.close()
    }
}
