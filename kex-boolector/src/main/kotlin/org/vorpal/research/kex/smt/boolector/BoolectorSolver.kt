package org.vorpal.research.kex.smt.boolector

import org.vorpal.research.boolector.Btor
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexReal
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.isArray
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.smt.AbstractSMTSolver
import org.vorpal.research.kex.smt.MemoryShape
import org.vorpal.research.kex.smt.Solver
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.ClassAccessTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.ConstLongTerm
import org.vorpal.research.kex.state.term.ConstStringTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.collectPointers
import org.vorpal.research.kex.state.transformer.collectVariables
import org.vorpal.research.kex.state.transformer.memspace
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import kotlin.math.log2

private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)

private typealias MemoryState = MutableMap<Term, Term>
private typealias MemoryPair = Pair<MemoryState, MemoryState>

@Solver("boolector")
class BoolectorSolver(
    private val executionContext: ExecutionContext
) : BoolectorNativeLoader(), AbstractSMTSolver {
    private val ef = BoolectorExprFactory()

    override fun isReachable(state: PredicateState) =
        isPathPossible(state, state.path)

    override fun isPathPossible(state: PredicateState, path: PredicateState) = isViolated(state, path)

    override fun isViolated(state: PredicateState, query: PredicateState): Result {
        if (logQuery) {
            log.run {
                debug("Boolector solver check")
                debug("State: $state")
                debug("Query: $query")
            }
        }

        val ctx = BoolectorContext(ef)

        val converter = BoolectorConverter(executionContext)
        converter.init(state, ef)
        val boolectorState = converter.convert(state, ef, ctx)
        val boolectorQuery = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(boolectorState, boolectorQuery)
        log.debug("Check finished")
        return when (result) {
            Btor.Status.UNSAT -> Result.UnsatResult()
            Btor.Status.UNKNOWN -> Result.UnknownResult("should not happen")
            Btor.Status.SAT -> Result.SatResult(collectModel(ctx, state))
        }
    }

    private fun check(state: Bool_, query: Bool_): Btor.Status {
        state.asAxiom().assertForm()
        ef.buildConstClassAxioms().asAxiom().assertForm()
        query.axiom.assertForm()
        query.expr.assertForm()

        if (logFormulae) {
            log.debug(ef.ctx.dumpSmt2())
        }
        log.debug("Running Boolector solver")
        val result = ef.ctx.check() ?: unreachable { log.error("Solver error") }

        log.debug("Solver finished")

        return result
    }

    private fun BoolectorContext.recoverBitvectorProperty(
        ptr: Term,
        memspace: Int,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = BoolectorConverter(executionContext).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val startProp = getBitvectorInitialProperty(memspace, name)
        val endProp = getBitvectorProperty(memspace, name)

        val startV = startProp.load(ptrExpr)
        val endV = endProp.load(ptrExpr)

        val modelStartV = BoolectorUnlogic.undo(startV.expr)
        val modelEndV = BoolectorUnlogic.undo(endV.expr)
        return modelStartV to modelEndV
    }

    private fun BoolectorContext.recoverProperty(
        ptr: Term,
        memspace: Int,
        type: KexType,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = BoolectorConverter(executionContext).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val typeSize = BoolectorExprFactory.getTypeSize(type)
        val startProp = when (typeSize) {
            TypeSize.WORD -> getWordInitialProperty(memspace, name)
            TypeSize.DWORD -> getDWordInitialProperty(memspace, name)
        }
        val endProp = when (typeSize) {
            TypeSize.WORD -> getWordProperty(memspace, name)
            TypeSize.DWORD -> getDWordProperty(memspace, name)
        }

        val startV = startProp.load(ptrExpr)
        val endV = endProp.load(ptrExpr)

        val modelStartV = BoolectorUnlogic.undo(startV.expr)
        val modelEndV = BoolectorUnlogic.undo(endV.expr)
        return modelStartV to modelEndV
    }

    private fun MutableMap<Int, MutableMap<String, MemoryPair>>.recoverProperty(
        ctx: BoolectorContext,
        ptr: Term,
        memspace: Int,
        type: KexType,
        name: String
    ) {
        val ptrExpr = BoolectorConverter(executionContext).convert(ptr, ef, ctx) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = BoolectorUnlogic.undo(ptrExpr.expr)

        val (modelStartT, modelEndT) = ctx.recoverProperty(ptr, memspace, type, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
    }

    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverBitvectorProperty(
        ctx: BoolectorContext,
        ptr: Term,
        memspace: Int,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = BoolectorConverter(executionContext).convert(ptr, ef, ctx) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = BoolectorUnlogic.undo(ptrExpr.expr)

        val (modelStartT, modelEndT) = ctx.recoverBitvectorProperty(ptr, memspace, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
        return modelStartT to modelEndT
    }

    private fun collectModel(ctx: BoolectorContext, vararg states: PredicateState): SMTModel {
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.associateWith {
            val expr = BoolectorConverter(executionContext).convert(it, ef, ctx)
            val boolectorExpr = expr.expr

            // this is needed because boolector represents real numbers as integers
            val undone = BoolectorUnlogic.undo(boolectorExpr)
            val actualValue = when (it.type) {
                is KexReal -> when (undone) {
                    is ConstIntTerm -> term { const(undone.value.toFloat()) }
                    is ConstLongTerm -> term { const(undone.value.toDouble()) }
                    else -> unreachable {
                        log.error("Unexpected Integer term when trying to reanimate floating point value: $undone")
                    }
                }

                else -> undone
            }
            actualValue
        }.toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val properties = hashMapOf<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val arrays = hashMapOf<Int, MutableMap<Term, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val typeMap = hashMapOf<Term, KexType>()

        for ((type, value) in ef.typeMap) {
            val index = when (val actualValue = BoolectorUnlogic.undo(value.expr)) {
                is ConstStringTerm -> term { const(actualValue.value.indexOf('1')) }
                else -> term { const(log2(actualValue.numericValue.toDouble()).toInt()) }
            }
            typeMap[index] = type.kexType
        }

        for (ptr in ptrs) {
            val memspace = ptr.memspace

            when (ptr) {
                is ArrayLoadTerm -> {
                }

                is ArrayIndexTerm -> {
                    val arrayPtrExpr = BoolectorConverter(executionContext).convert(ptr.arrayRef, ef, ctx) as? Ptr_
                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
                    val indexExpr = BoolectorConverter(executionContext).convert(ptr.index, ef, ctx) as? Int_
                        ?: unreachable { log.error("Non integer expr for index in $ptr") }

                    val modelPtr = BoolectorUnlogic.undo(arrayPtrExpr.expr)
                    val modelIndex = BoolectorUnlogic.undo(indexExpr.expr)

                    val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, memspace)
                    val modelArray = ctx.readArrayMemory(arrayPtrExpr, memspace)

                    val cast = { arrayVal: DWord_ ->
                        when (BoolectorExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
                            TypeSize.WORD -> Word_.forceCast(arrayVal)
                            TypeSize.DWORD -> arrayVal
                        }
                    }
                    val initialValue = BoolectorUnlogic.undo(
                        cast(
                            DWord_.forceCast(modelStartArray.load(indexExpr))
                        ).expr
                    )
                    val value = BoolectorUnlogic.undo(
                        cast(
                            DWord_.forceCast(modelArray.load(indexExpr))
                        ).expr
                    )

                    val arrayPair = arrays.getOrPut(memspace, ::hashMapOf).getOrPut(modelPtr) {
                        hashMapOf<Term, Term>() to hashMapOf()
                    }
                    arrayPair.first[modelIndex] = initialValue
                    arrayPair.second[modelIndex] = value
                }

                is FieldLoadTerm -> {
                }

                is FieldTerm -> {
                    val name = "${ptr.klass}.${ptr.fieldName}"
                    properties.recoverProperty(ctx, ptr.owner, memspace, (ptr.type as KexReference).reference, name)
                    properties.recoverBitvectorProperty(ctx, ptr.owner, memspace, "type")
                }

                else -> {
                    val startMem = ctx.getWordInitialMemory(memspace)
                    val endMem = ctx.getWordMemory(memspace)

                    val ptrExpr = BoolectorConverter(executionContext).convert(ptr, ef, ctx) as? Ptr_
                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

                    val startV = startMem.load(ptrExpr)
                    val endV = endMem.load(ptrExpr)

                    val modelPtr = BoolectorUnlogic.undo(ptrExpr.expr)
                    val modelStartV = BoolectorUnlogic.undo(startV.expr)
                    val modelEndV = BoolectorUnlogic.undo(endV.expr)

                    memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
                    memories.getValue(memspace).first[modelPtr] = modelStartV
                    memories.getValue(memspace).second[modelPtr] = modelEndV

                    properties.recoverBitvectorProperty(ctx, ptr, memspace, "type")

                    if (ptr.type.isArray) {
                        properties.recoverProperty(ctx, ptr, memspace, KexInt, "length")
                    } else if (ptr is ConstClassTerm || ptr is ClassAccessTerm) {
                        properties.recoverBitvectorProperty(ctx, ptr, memspace, ConstClassTerm.TYPE_INDEX_PROPERTY)
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

    override fun close() = ef.ctx.release()
}
