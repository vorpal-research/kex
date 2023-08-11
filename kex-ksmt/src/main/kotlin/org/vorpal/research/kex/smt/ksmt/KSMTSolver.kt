@file:OptIn(ExperimentalTime::class)

package org.vorpal.research.kex.smt.ksmt

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import io.ksmt.expr.KAndBinaryExpr
import io.ksmt.expr.KAndNaryExpr
import io.ksmt.expr.KExpr
import io.ksmt.runner.core.WorkerInitializationFailedException
import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverException
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.portfolio.KPortfolioSolver
import io.ksmt.solver.portfolio.KPortfolioSolverManager
import io.ksmt.solver.runner.KSolverExecutorTimeoutException
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KSort
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.isArray
import org.vorpal.research.kex.ktype.isString
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.smt.AbstractAsyncIncrementalSMTSolver
import org.vorpal.research.kex.smt.AbstractAsyncSMTSolver
import org.vorpal.research.kex.smt.AbstractIncrementalSMTSolver
import org.vorpal.research.kex.smt.AbstractSMTSolver
import org.vorpal.research.kex.smt.AsyncIncrementalSolver
import org.vorpal.research.kex.smt.AsyncSolver
import org.vorpal.research.kex.smt.IncrementalSolver
import org.vorpal.research.kex.smt.MemoryShape
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.smt.Solver
import org.vorpal.research.kex.smt.ksmt.KSMTEngine.asExpr
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.ClassAccessTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.ConstStringTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.collectPointers
import org.vorpal.research.kex.state.transformer.collectTypes
import org.vorpal.research.kex.state.transformer.collectVariables
import org.vorpal.research.kex.state.transformer.getConstStringMap
import org.vorpal.research.kex.state.transformer.memspace
import org.vorpal.research.kex.util.kapitalize
import org.vorpal.research.kex.util.with
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull
import kotlin.math.log2
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private val timeout = kexConfig.getIntValue("smt", "timeout", 3)
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)
private val printSMTLib = kexConfig.getBooleanValue("smt", "logSMTLib", false)
private val maxArrayLength = kexConfig.getIntValue("smt", "maxArrayLength", 1000)
private val ksmtRunners = kexConfig.getIntValue("ksmt", "runners", 4)
private val ksmtSolvers = kexConfig.getMultipleStringValue("ksmt", "solver")
private val ksmtSeed = kexConfig.getIntValue("ksmt", "seed", 42)

@Suppress("UNCHECKED_CAST")
@AsyncSolver("ksmt")
@AsyncIncrementalSolver("ksmt")
@Solver("ksmt")
@IncrementalSolver("ksmt")
class KSMTSolver(
    private val executionContext: ExecutionContext
) : AbstractSMTSolver, AbstractAsyncSMTSolver, AbstractIncrementalSMTSolver, AbstractAsyncIncrementalSMTSolver {
    companion object {
        private val portfolioSolverManager: KPortfolioSolverManager by lazy {
            KPortfolioSolverManager(
                solvers = ksmtSolvers.map {
                    Class.forName("io.ksmt.solver.${it}.K${it.kapitalize()}Solver").kotlin
                            as KClass<out KSolver<out KSolverConfiguration>>
                },
                portfolioPoolSize = ksmtRunners,
                hardTimeout = timeout.seconds * 2,
                workerProcessIdleTimeout = 10.seconds,
            )
        }
    }

    private val ef = KSMTExprFactory()


    override fun isReachable(state: PredicateState): Result = runBlocking {
        isReachableAsync(state)
    }

    override fun isPathPossible(state: PredicateState, path: PredicateState): Result = runBlocking {
        isPathPossibleAsync(state, path)
    }

    override fun isViolated(state: PredicateState, query: PredicateState): Result = runBlocking {
        isViolatedAsync(state, query)
    }

    override suspend fun isReachableAsync(state: PredicateState) =
        isPathPossibleAsync(state, state.path)

    override suspend fun isPathPossibleAsync(state: PredicateState, path: PredicateState): Result =
        check(state, path) { it }


    override suspend fun isViolatedAsync(state: PredicateState, query: PredicateState): Result =
        check(state, query) { !it }

    suspend fun check(state: PredicateState, query: PredicateState, queryBuilder: (Bool_) -> Bool_): Result = try {
        if (logQuery) {
            log.run {
                debug("KSMT solver check")
                debug("State: {}", state)
                debug("Query: {}", query)
            }
        }

        val types = collectTypes(executionContext, state).filter { it !is KexNull }
            .mapTo(mutableSetOf()) { it.getKfgType(executionContext.types) }
        ef.initTypes(types)
        ef.initStrings(getConstStringMap(state))

        val ctx = KSMTContext(ef)
        val converter = KSMTConverter(executionContext)
        val ksmtState = converter.convert(state, ef, ctx)
        val ksmtQuery = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(ksmtState, queryBuilder(ksmtQuery))
        log.debug("Check finished")
        when (result.first) {
            KSolverStatus.UNSAT -> Result.UnsatResult
            KSolverStatus.UNKNOWN -> Result.UnknownResult(result.second as String)
            KSolverStatus.SAT -> Result.SatResult(collectModel(ctx, result.second as KModel, state))
        }
    } catch (e: KSolverException) {
        when (e.cause) {
            is TimeoutCancellationException -> Result.UnknownResult(e.message ?: "Exception in KSMT")
            is KSolverExecutorTimeoutException -> Result.UnknownResult(e.message ?: "Exception in KSMT")
            else -> {
                log.warn("KSMT thrown an exception during check", e)
                throw e
            }
        }
    } catch (e: WorkerInitializationFailedException) {
        if (e.cause !is TimeoutCancellationException) {
            log.warn("KSMT thrown an exception during worker initialization", e)
        }
        Result.UnknownResult(e.message ?: "Exception in KSMT")
    }


    @Suppress("unused")
    private suspend fun reduce(state: Bool_, query: Bool_, errorCondition: (KSolverException) -> Boolean) {
        var actualState = state
        var actualQuery = query
        var currentState = actualState
        var currentQuery = actualQuery
        repeat(1000) {
            currentState = reduce(actualState)
            currentQuery = reduce(actualQuery)
            val reproduced = try {
                check(currentState, currentQuery)
                false
            } catch (e: KSolverException) {
                errorCondition(e)
            }
            if (reproduced) {
                actualState = currentState
                actualQuery = currentQuery
            } else {
                currentState = actualState
                currentQuery = actualQuery
            }
        }

        check(currentState, currentQuery)
    }

    private fun reduce(expr: Bool_): Bool_ =
        Bool_(expr.ctx, reduce(expr.expr as KExpr<*>), reduce(expr.axiom as KExpr<*>))

    private fun <T : KSort> reduce(state: KExpr<T>): KExpr<T> = when {
        state is KAndBinaryExpr -> when (executionContext.random.nextInt(100)) {
            in 0..2 -> when (executionContext.random.nextBoolean()) {
                true -> state.lhs
                else -> state.rhs
            }

            else -> ef.ctx.mkAnd(reduce(state.lhs), reduce(state.rhs))
        } as KExpr<T>

        state is KAndNaryExpr -> when (executionContext.random.nextInt(100)) {
            in 0..2 -> {
                val nth = executionContext.random.nextInt(state.args.size)
                ef.ctx.mkAnd(state.args.filterIndexed { index, _ -> index != nth })
            }

            else -> ef.ctx.mkAnd(state.args.map { reduce(it) })
        } as KExpr<T>

        state.sort is KBoolSort -> when (executionContext.random.nextInt(100)) {
            in 0..2 -> ef.ctx.mkBool(executionContext.random.nextBoolean())
            else -> state
        } as KExpr<T>

        state.sort is KBvSort -> when (executionContext.random.nextInt(100)) {
            in 0..2 -> ef.ctx.mkBv(executionContext.random.nextInt(100), state.sort as KBvSort)
            else -> state
        } as KExpr<T>

        else -> state
    }

    private fun stringify(state: Bool_, query: Bool_, softConstraints: List<Bool_> = emptyList()): String = KZ3Solver(ef.ctx).use {
        it.assert(state.asAxiom() as KExpr<KBoolSort>)
        it.assert(ef.buildConstClassAxioms().asAxiom() as KExpr<KBoolSort>)
        it.assert(query.axiom as KExpr<KBoolSort>)
        it.assert(query.expr as KExpr<KBoolSort>)
        for (softConstraint in softConstraints) {
            it.assert(softConstraint.asAxiom() as KExpr<KBoolSort>)
        }

        val solverProp = KZ3Solver::class.declaredMemberProperties.first { prop -> prop.name == "solver" }
        solverProp.isAccessible = true
        val z3SolverInternal = solverProp.get(it) as com.microsoft.z3.Solver
        z3SolverInternal.toString()
    }

    private suspend fun check(state: Bool_, query: Bool_): Pair<KSolverStatus, Any> = buildSolver().use { solver ->
        if (logFormulae) {
            log.run {
                debug("State: {}", state)
                debug("Query: {}", query)
            }
        }

        if (printSMTLib) {
            log.debug("SMTLib formula:")
            log.debug(stringify(state, query))
        }

        solver.assertAsync(state.asAxiom() as KExpr<KBoolSort>)
        solver.assertAsync(ef.buildConstClassAxioms().asAxiom() as KExpr<KBoolSort>)
        solver.assertAsync(query.axiom as KExpr<KBoolSort>)
        solver.assertAsync(query.expr as KExpr<KBoolSort>)
        log.debug("Running KSMT solver")
        val result = solver.checkAsync(timeout.seconds)
        log.debug("Solver finished")

        return when (result) {
            KSolverStatus.SAT -> `try`<Pair<KSolverStatus, Any>> {
                val model = solver.modelAsync()
                if (logFormulae) log.debug(model)
                result to model
            }.getOrElse {
                KSolverStatus.UNKNOWN to (it.message ?: "Exception during model acquisition")
            }

            KSolverStatus.UNSAT -> {
                val core = tryOrNull { solver.unsatCoreAsync() } ?: "Solver executor is not alive"
                log.debug("Unsat core: {}", core)
                result to core
            }

            KSolverStatus.UNKNOWN -> {
                val reason = tryOrNull { solver.reasonOfUnknownAsync() } ?: "Solver executor is not alive"
                log.debug(reason)
                result to reason
            }
        }
    }

    private suspend fun buildSolver(): KPortfolioSolver {
        if (!currentCoroutineContext().isActive) yield()
        return portfolioSolverManager.createPortfolioSolver(ef.ctx).also {
            it.configureAsync {
                setIntParameter("random_seed", ksmtSeed)
                setIntParameter("seed", ksmtSeed)
            }
        }
    }

    private fun KSMTContext.recoverBitvectorProperty(
        ptr: Term,
        memspace: Int,
        model: KModel,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = KSMTConverter(executionContext, noAxioms = true).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val startProp = getBitvectorInitialProperty(memspace, name)
        val endProp = getBitvectorProperty(memspace, name)

        val startV = startProp.load(ptrExpr)
        val endV = endProp.load(ptrExpr)

        val kCtx = factory.ctx
        val modelStartV = KSMTUnlogic.undo(model.eval(startV.expr.asExpr(kCtx), true), kCtx, model)
        val modelEndV = KSMTUnlogic.undo(model.eval(endV.expr.asExpr(kCtx), true), kCtx, model)
        return modelStartV to modelEndV
    }

    private fun KSMTContext.recoverProperty(
        ptr: Term,
        memspace: Int,
        type: KexType,
        model: KModel,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = KSMTConverter(executionContext, noAxioms = true).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val typeSize = KSMTExprFactory.getTypeSize(type)
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

        val kCtx = factory.ctx
        val modelStartV = KSMTUnlogic.undo(model.eval(startV.expr.asExpr(kCtx), true), kCtx, model)
        val modelEndV = KSMTUnlogic.undo(model.eval(endV.expr.asExpr(kCtx), true), kCtx, model)
        return modelStartV to modelEndV
    }

    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverProperty(
        ctx: KSMTContext,
        ptr: Term,
        memspace: Int,
        type: KexType,
        model: KModel,
        name: String
    ): Pair<Term, Term> {
        val kCtx = ctx.factory.ctx
        val ptrExpr = KSMTConverter(executionContext, noAxioms = true).convert(ptr, ef, ctx) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = KSMTUnlogic.undo(model.eval(ptrExpr.expr.asExpr(kCtx), true), kCtx, model)

        val (modelStartT, modelEndT) = ctx.recoverProperty(ptr, memspace, type, model, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
        return modelStartT to modelEndT
    }

    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverBitvectorProperty(
        ctx: KSMTContext,
        ptr: Term,
        memspace: Int,
        model: KModel,
        name: String
    ): Pair<Term, Term> {
        val kCtx = ctx.factory.ctx
        val ptrExpr = KSMTConverter(executionContext, noAxioms = true).convert(ptr, ef, ctx) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = KSMTUnlogic.undo(model.eval(ptrExpr.expr.asExpr(kCtx), true), kCtx, model)

        val (modelStartT, modelEndT) = ctx.recoverBitvectorProperty(ptr, memspace, model, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
        return modelStartT to modelEndT
    }

    private fun collectModel(ctx: KSMTContext, model: KModel, vararg states: PredicateState): SMTModel {
        val kCtx = ctx.factory.ctx
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.associateWith {
            val expr = KSMTConverter(executionContext, noAxioms = true).convert(it, ef, ctx)
            val ksmtExpr = expr.expr

            val evaluatedExpr = model.eval(ksmtExpr.asExpr(kCtx), true)
            KSMTUnlogic.undo(evaluatedExpr, kCtx, model)
        }.toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val properties = hashMapOf<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val arrays = hashMapOf<Int, MutableMap<Term, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val typeMap = hashMapOf<Term, KexType>()

        for ((type, value) in ef.typeMap) {
            val index = when (val actualValue = KSMTUnlogic.undo(value.expr.asExpr(kCtx), kCtx, model)) {
                is ConstStringTerm -> term {
                    const(actualValue.value.length - actualValue.value.indexOf('1') - 1)
                }

                else -> term { const(log2(actualValue.numericValue.toDouble()).toInt()) }
            }
            typeMap[index] = type.kexType
        }

        val indices = hashSetOf<Term>()
        for (ptr in ptrs) {
            val memspace = ptr.memspace

            when (ptr) {
                is ArrayLoadTerm -> {}
                is ArrayIndexTerm -> {
                    val arrayPtrExpr =
                        KSMTConverter(executionContext, noAxioms = true).convert(ptr.arrayRef, ef, ctx) as? Ptr_
                            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
                    val indexExpr =
                        KSMTConverter(executionContext, noAxioms = true).convert(ptr.index, ef, ctx) as? Int_
                            ?: unreachable { log.error("Non integer expr for index in $ptr") }

                    val modelPtr = KSMTUnlogic.undo(
                        model.eval(arrayPtrExpr.expr.asExpr(kCtx), true),
                        kCtx,
                        model
                    )
                    val modelIndex = KSMTUnlogic.undo(
                        model.eval(indexExpr.expr.asExpr(kCtx), true),
                        kCtx,
                        model
                    )

                    val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, ptr.arrayRef.memspace)
                    val modelArray = ctx.readArrayMemory(arrayPtrExpr, ptr.arrayRef.memspace)

                    val cast = { arrayVal: DWord_ ->
                        when (KSMTExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
                            TypeSize.WORD -> Word_.forceCast(arrayVal)
                            TypeSize.DWORD -> arrayVal
                        }
                    }
                    val initialValue = KSMTUnlogic.undo(
                        model.eval(
                            cast(
                                DWord_.forceCast(modelStartArray.load(indexExpr))
                            ).expr.asExpr(kCtx),
                            true
                        ), kCtx, model
                    )
                    val value = KSMTUnlogic.undo(
                        model.eval(
                            cast(
                                DWord_.forceCast(modelArray.load(indexExpr))
                            ).expr.asExpr(kCtx),
                            true
                        ), kCtx, model
                    )

                    val arrayPair = arrays.getOrPut(ptr.arrayRef.memspace, ::hashMapOf).getOrPut(modelPtr) {
                        hashMapOf<Term, Term>() to hashMapOf()
                    }
                    arrayPair.first[modelIndex] = initialValue
                    arrayPair.second[modelIndex] = value
                }

                is FieldLoadTerm -> {}
                is FieldTerm -> {
                    val name = "${ptr.klass}.${ptr.fieldName}"
                    properties.recoverProperty(
                        ctx,
                        ptr.owner,
                        memspace,
                        (ptr.type as KexReference).reference,
                        model,
                        name
                    )
                    properties.recoverBitvectorProperty(ctx, ptr.owner, memspace, model, "type")
                }

                else -> {
                    val startMem = ctx.getWordInitialMemory(memspace)
                    val endMem = ctx.getWordMemory(memspace)

                    val ptrExpr = KSMTConverter(executionContext, noAxioms = true).convert(ptr, ef, ctx) as? Ptr_
                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

                    val startV = startMem.load(ptrExpr)
                    val endV = endMem.load(ptrExpr)

                    val modelPtr = KSMTUnlogic.undo(model.eval(ptrExpr.expr.asExpr(kCtx), true), kCtx, model)
                    val modelStartV = KSMTUnlogic.undo(model.eval(startV.expr.asExpr(kCtx), true), kCtx, model)
                    val modelEndV = KSMTUnlogic.undo(model.eval(endV.expr.asExpr(kCtx), true), kCtx, model)

                    memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
                    memories.getValue(memspace).first[modelPtr] = modelStartV
                    memories.getValue(memspace).second[modelPtr] = modelEndV
                    if (ptr.type.isString) {
//                        val modelStartStr = ctx.readInitialStringMemory(ptrExpr, memspace)
//                        val modelStr = ctx.readStringMemory(ptrExpr, memspace)
//                        val startStringVal = KSMTUnlogic.undo(model.evaluate(modelStartStr.expr, true))
//                        val stringVal = KSMTUnlogic.undo(model.evaluate(modelStr.expr, true))
//                        strings.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
//                        strings.getValue(memspace).first[modelPtr] = startStringVal
//                        strings.getValue(memspace).second[modelPtr] = stringVal
                    } else if (ptr.type.isArray) {
                        val (startLength, endLength) = properties.recoverProperty(
                            ctx,
                            ptr,
                            memspace,
                            KexInt,
                            model,
                            "length"
                        )
                        var (initialLength, finalLength) = (startLength.numericValue.toInt() to endLength.numericValue.toInt())
                        // this is fucked up
                        if (initialLength > maxArrayLength) {
                            log.warn("Reanimated length of an array is too big: $initialLength")
                            initialLength = maxArrayLength
                        }
                        // this is fucked up
                        if (finalLength > maxArrayLength) {
                            log.warn("Reanimated length of an array is too big: $finalLength")
                            finalLength = maxArrayLength
                        }
                        val maxLen = maxOf(initialLength, finalLength)
                        properties[memspace]!!["length"]!!.first[modelPtr] = term { const(initialLength) }
                        properties[memspace]!!["length"]!!.second[modelPtr] = term { const(finalLength) }
                        for (i in 0 until maxLen) {
                            val indexTerm = term { ptr[i] }
                            if (indexTerm !in ptrs)
                                indices += indexTerm
                        }
                    } else if (ptr is ConstClassTerm || ptr is ClassAccessTerm) {
                        properties.recoverBitvectorProperty(
                            ctx,
                            ptr,
                            memspace,
                            model,
                            ConstClassTerm.TYPE_INDEX_PROPERTY
                        )
                    }

                    properties.recoverBitvectorProperty(ctx, ptr, memspace, model, "type")

                    ktassert(assignments.getOrPut(ptr) { modelPtr } == modelPtr)
                }
            }
        }
        for (ptr in indices) {
            ptr as ArrayIndexTerm
            val memspace = ptr.arrayRef.memspace
            val arrayPtrExpr = KSMTConverter(executionContext, noAxioms = true).convert(ptr.arrayRef, ef, ctx) as? Ptr_
                ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
            val indexExpr = KSMTConverter(executionContext, noAxioms = true).convert(ptr.index, ef, ctx) as? Int_
                ?: unreachable { log.error("Non integer expr for index in $ptr") }

            val modelPtr =
                KSMTUnlogic.undo(model.eval(arrayPtrExpr.expr.asExpr(kCtx), true), kCtx, model)
            val modelIndex =
                KSMTUnlogic.undo(model.eval(indexExpr.expr.asExpr(kCtx), true), kCtx, model)

            val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, memspace)
            val modelArray = ctx.readArrayMemory(arrayPtrExpr, memspace)

            val cast = { arrayVal: DWord_ ->
                when (KSMTExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
                    TypeSize.WORD -> Word_.forceCast(arrayVal)
                    TypeSize.DWORD -> arrayVal
                }
            }
            val initialValue = KSMTUnlogic.undo(
                model.eval(
                    cast(
                        DWord_.forceCast(modelStartArray.load(indexExpr))
                    ).expr.asExpr(kCtx),
                    true
                ), kCtx, model
            )
            val value = KSMTUnlogic.undo(
                model.eval(
                    cast(
                        DWord_.forceCast(modelArray.load(indexExpr))
                    ).expr.asExpr(kCtx),
                    true
                ), kCtx, model
            )

            val arrayPair = arrays.getOrPut(memspace, ::hashMapOf).getOrPut(modelPtr) {
                hashMapOf<Term, Term>() to hashMapOf()
            }
            arrayPair.first[modelIndex] = initialValue
            arrayPair.second[modelIndex] = value
        }
        return SMTModel(
            assignments,
            memories.map { (memspace, pair) -> memspace to MemoryShape(pair.first, pair.second) }.toMap(),
            properties.map { (memspace, names) ->
                memspace to names.map { (name, pair) -> name to MemoryShape(pair.first, pair.second) }.toMap()
            }.toMap(),
            arrays.map { (memspace, values) ->
                memspace to values.map { (addr, pair) -> addr to MemoryShape(pair.first, pair.second) }.toMap()
            }.toMap(),
            typeMap
        )
    }

    override fun isSatisfiable(state: PredicateState, queries: List<PredicateQuery>): List<Result> = runBlocking {
        isSatisfiableAsync(state, queries)
    }

    override suspend fun isSatisfiableAsync(state: PredicateState, queries: List<PredicateQuery>): List<Result> = try {
        if (logQuery) {
            log.run {
                debug("KSMT solver check")
                debug("State: {}", state)
                debug("Queries: {}", queries.joinToString("\n"))
            }
        }

        val allTypes = buildSet {
            addAll(collectTypes(executionContext, state))
            for (query in queries) {
                addAll(collectTypes(executionContext, query.hardConstraints))
                for (softConstraint in query.softConstraints) {
                    addAll(collectTypes(executionContext, query.hardConstraints))
                }
            }
        }.filter { it !is KexNull }.mapTo(mutableSetOf()) { it.getKfgType(executionContext.types) }
        val allConstStrings = buildMap {
            putAll(getConstStringMap(state))
            for (query in queries) {
                putAll(getConstStringMap(query.hardConstraints))
            }
        }
        ef.initTypes(allTypes)
        ef.initStrings(allConstStrings)

        val ctx = KSMTContext(ef)
        val converter = KSMTConverter(executionContext)
        val ksmtState = converter.convert(state, ef, ctx)
        val ksmtQueries = queries.map { (hard, soft) ->
            val ctxCopy = KSMTContext(ctx)
            Triple(converter.convert(hard, ef, ctx), soft.map { converter.convert(it, ef, ctx) }, ctxCopy)
        }

        log.debug("Check started")
        val results = checkIncremental(ksmtState, ksmtQueries)
        log.debug("Check finished")
        results.mapIndexed { index, (status, any, ctx) ->
            when (status) {
                KSolverStatus.UNSAT -> Result.UnsatResult
                KSolverStatus.UNKNOWN -> Result.UnknownResult(any as String)
                KSolverStatus.SAT -> Result.SatResult(
                    collectModel(
                        ctx,
                        any as KModel,
                        state + queries[index].hardConstraints
                    )
                )
            }
        }
    } catch (e: KSolverException) {
        when (e.cause) {
            is TimeoutCancellationException ->
                queries.map { Result.UnknownResult(e.message ?: "Exception in KSMT") }

            is KSolverExecutorTimeoutException ->
                queries.map { Result.UnknownResult(e.message ?: "Exception in KSMT") }

            else -> {
                log.warn("KSMT thrown an exception during check", e)
                throw e
            }
        }
    } catch (e: WorkerInitializationFailedException) {
        if (e.cause !is TimeoutCancellationException) {
            log.warn("KSMT thrown an exception during worker initialization", e)
        }
        queries.map { Result.UnknownResult(e.message ?: "Exception in KSMT") }
    }

    private suspend fun checkIncremental(
        state: Bool_,
        queries: List<Triple<Bool_, List<Bool_>, KSMTContext>>
    ): List<Triple<KSolverStatus, Any, KSMTContext>> = buildSolver().use { solver ->
        solver.assertAndTrackAsync(
            state.asAxiom() as KExpr<KBoolSort>
        )
        solver.assertAndTrackAsync(
            ef.buildConstClassAxioms().asAxiom() as KExpr<KBoolSort>
        )
        solver.pushAsync()

        return queries.map { (hardConstraints, softConstraints, ctx) ->
            solver.assertAndTrackAsync(
                hardConstraints.asAxiom() as KExpr<KBoolSort>
            )
            val softConstraintsSet = when {
                softConstraints.isNotEmpty() -> {
                    solver.pushAsync()
                    softConstraints.mapTo(mutableSetOf()) { softConstraint ->
                        (softConstraint.asAxiom() as KExpr<KBoolSort>).also {
                            solver.assertAndTrackAsync(it)
                        }
                    }
                }

                else -> emptySet()
            }

            log.debug("Running KSMT solver")
            if (printSMTLib) {
                log.debug("SMTLib formula:")
                log.debug(stringify(state, hardConstraints, softConstraints))
            }

            when (val result = solver.checkAndMinimize(softConstraintsSet)) {
                KSolverStatus.SAT -> `try`<Pair<KSolverStatus, Any>> {
                    val model = solver.modelAsync()
                    if (logFormulae) log.debug(model)
                    result to model
                }.getOrElse {
                    KSolverStatus.UNKNOWN to (it.message ?: "Exception during model acquisition")
                }

                KSolverStatus.UNSAT -> {
                    val core = tryOrNull { solver.unsatCoreAsync() } ?: "Solver executor is not alive"
                    log.debug("Unsat core: {}", core)
                    result to core
                }

                KSolverStatus.UNKNOWN -> {
                    val reason = tryOrNull { solver.reasonOfUnknownAsync() } ?: "unknown"
                    log.debug(reason)
                    result to reason
                }
            }.also {
                solver.popAsync(1u)
                if (softConstraints.isNotEmpty()) {
                    solver.popAsync(1u)
                }
                solver.pushAsync()
            }.with(ctx)
        }
    }

    private suspend fun KPortfolioSolver.checkAndMinimize(
        softConstraintsMap: Set<KExpr<KBoolSort>>
    ): KSolverStatus {
        var result = this.checkAsync(timeout.seconds)
        return when (result) {
            KSolverStatus.UNSAT -> {
                while (result == KSolverStatus.UNSAT && softConstraintsMap.isNotEmpty()) {
                    val softCopies = softConstraintsMap.toMutableSet()
                    val core = tryOrNull { this.unsatCoreAsync().toSet() } ?: return result
                    if (core.all { it !in softCopies }) break
                    else {
                        this.popAsync(1u)
                        this.pushAsync()
                        for (key in core) softCopies.remove(key)
                        for (softConstraint in softCopies) {
                            this.assertAndTrackAsync(softConstraint)
                        }
                        result = this.checkAsync(timeout.seconds)
                    }
                }
                result
            }

            else -> result
        }
    }

    override fun close() {
        ef.ctx.close()
    }
}
