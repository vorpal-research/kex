package org.vorpal.research.kex.smt.z3

import com.microsoft.z3.BoolExpr
import com.microsoft.z3.Global
import com.microsoft.z3.Model
import com.microsoft.z3.Status
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.smt.*
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kex.state.transformer.collectPointers
import org.vorpal.research.kex.state.transformer.collectTypes
import org.vorpal.research.kex.state.transformer.collectVariables
import org.vorpal.research.kex.state.transformer.getConstStringMap
import org.vorpal.research.kex.state.transformer.memspace
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import kotlin.math.log2
import com.microsoft.z3.Solver as NativeSolver

private val timeout = kexConfig.getIntValue("smt", "timeout", 3) * 1000
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
private val logFormulae = kexConfig.getBooleanValue("smt", "logFormulae", false)
private val printSMTLib = kexConfig.getBooleanValue("smt", "logSMTLib", false)
private val simplifyFormulae = kexConfig.getBooleanValue("smt", "simplifyFormulae", false)
private val maxArrayLength = kexConfig.getIntValue("smt", "maxArrayLength", 1000)

@AsyncSolver("z3")
@AsyncIncrementalSolver("z3")
@Solver("z3")
@IncrementalSolver("z3")
class Z3Solver(
    private val executionContext: ExecutionContext
) : Z3NativeLoader(), AbstractSMTSolver, AbstractAsyncSMTSolver, AbstractIncrementalSMTSolver,
    AbstractAsyncIncrementalSMTSolver {
    private val ef = Z3ExprFactory()

    override fun isReachable(state: PredicateState) =
        isPathPossible(state, state.path)

    override fun isPathPossible(state: PredicateState, path: PredicateState): Result = check(state, path) { it }


    override fun isViolated(state: PredicateState, query: PredicateState): Result = check(state, query) { !it }

    private fun check(state: PredicateState, query: PredicateState, queryBuilder: (Bool_) -> Bool_): Result {
        if (logQuery) {
            log.run {
                debug("Z3 solver check")
                debug("State: $state")
                debug("Query: $query")
            }
        }

        val ctx = Z3Context(ef)

        val converter = Z3Converter(executionContext)
        converter.init(state, ef)
        val z3State = converter.convert(state, ef, ctx)
        val z3query = converter.convert(query, ef, ctx)

        log.debug("Check started")
        val result = check(z3State, queryBuilder(z3query))
        log.debug("Check finished")
        return when (result.first) {
            Status.UNSATISFIABLE -> Result.UnsatResult()
            Status.UNKNOWN -> Result.UnknownResult(result.second as String)
            Status.SATISFIABLE -> Result.SatResult(collectModel(ctx, result.second as Model, state))
        }
    }

    private fun check(state: Bool_, query: Bool_): Pair<Status, Any> {
        val solver = buildSolver()

        val (simplifiedState, simplifiedQuery) = when {
            simplifyFormulae -> state.simplify() to query.simplify()
            else -> state to query
        }

        if (logFormulae) {
            log.run {
                debug("State: $simplifiedState")
                debug("Query: $simplifiedQuery")
            }
        }

        solver.add(simplifiedState.asAxiom() as BoolExpr)
        solver.add(ef.buildConstClassAxioms().asAxiom() as BoolExpr)
        solver.add(simplifiedQuery.axiom as BoolExpr)
        solver.add(simplifiedQuery.expr as BoolExpr)

        log.debug("Running z3 solver")
        if (printSMTLib) {
            log.debug("SMTLib formula:")
            log.debug(solver)
        }
        val result = solver.check(query.expr as BoolExpr) ?: unreachable { log.error("Solver error") }
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

    private fun buildSolver(): NativeSolver {
        Z3Params.load().forEach { (name, value) ->
            Global.setParameter(name, value.toString())
        }

        val ctx = ef.ctx
        val solver = Z3Tactics.load().map {
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
        }.reduceOrNull { a, b -> ctx.andThen(a, b) }?.let { ctx.mkSolver(it) } ?: ctx.mkSolver()

        val parameters = ctx.mkParams()
        parameters.add("timeout", timeout)
        solver.setParameters(parameters)
        return solver
    }

    private fun Z3Context.recoverBitvectorProperty(
        ptr: Term,
        memspace: Int,
        model: Model,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val startProp = getBitvectorInitialProperty(memspace, name)
        val endProp = getBitvectorProperty(memspace, name)

        val startV = startProp.load(ptrExpr)
        val endV = endProp.load(ptrExpr)

        val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
        val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
        return modelStartV to modelEndV
    }

    private fun Z3Context.recoverProperty(
        ptr: Term,
        memspace: Int,
        type: KexType,
        model: Model,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr, ef, this) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val typeSize = Z3ExprFactory.getTypeSize(type)
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

        val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
        val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))
        return modelStartV to modelEndV
    }

    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverProperty(
        ctx: Z3Context,
        ptr: Term,
        memspace: Int,
        type: KexType,
        model: Model,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr, ef, ctx) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))

        val (modelStartT, modelEndT) = ctx.recoverProperty(ptr, memspace, type, model, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
        return modelStartT to modelEndT
    }

    private fun MutableMap<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>.recoverBitvectorProperty(
        ctx: Z3Context,
        ptr: Term,
        memspace: Int,
        model: Model,
        name: String
    ): Pair<Term, Term> {
        val ptrExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr, ef, ctx) as? Ptr_
            ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
        val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))

        val (modelStartT, modelEndT) = ctx.recoverBitvectorProperty(ptr, memspace, model, name)
        val typePair = this.getOrPut(memspace, ::hashMapOf).getOrPut(name) {
            hashMapOf<Term, Term>() to hashMapOf()
        }
        typePair.first[modelPtr] = modelStartT
        typePair.second[modelPtr] = modelEndT
        return modelStartT to modelEndT
    }

    private fun collectModel(ctx: Z3Context, model: Model, vararg states: PredicateState): SMTModel {
        val (ptrs, vars) = states.fold(setOf<Term>() to setOf<Term>()) { acc, ps ->
            acc.first + collectPointers(ps) to acc.second + collectVariables(ps)
        }

        val assignments = vars.associateWith {
            val expr = Z3Converter(executionContext, noAxioms = true).convert(it, ef, ctx)
            val z3expr = expr.expr

            val evaluatedExpr = model.evaluate(z3expr, true)
            Z3Unlogic.undo(evaluatedExpr)
        }.toMutableMap()

        val memories = hashMapOf<Int, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>()
        val properties = hashMapOf<Int, MutableMap<String, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val arrays = hashMapOf<Int, MutableMap<Term, Pair<MutableMap<Term, Term>, MutableMap<Term, Term>>>>()
        val typeMap = hashMapOf<Term, KexType>()

        for ((type, value) in ef.typeMap) {
            val index = when (val actualValue = Z3Unlogic.undo(value.expr)) {
                is ConstStringTerm -> term { const(actualValue.value.indexOf('1')) }
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
                    val arrayPtrExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr.arrayRef, ef, ctx) as? Ptr_
                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
                    val indexExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr.index, ef, ctx) as? Int_
                        ?: unreachable { log.error("Non integer expr for index in $ptr") }

                    val modelPtr = Z3Unlogic.undo(model.evaluate(arrayPtrExpr.expr, true))
                    val modelIndex = Z3Unlogic.undo(model.evaluate(indexExpr.expr, true))

                    val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, ptr.arrayRef.memspace)
                    val modelArray = ctx.readArrayMemory(arrayPtrExpr, ptr.arrayRef.memspace)

                    val cast = { arrayVal: DWord_ ->
                        when (Z3ExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
                            TypeSize.WORD -> Word_.forceCast(arrayVal)
                            TypeSize.DWORD -> arrayVal
                        }
                    }
                    val initialValue = Z3Unlogic.undo(
                        model.evaluate(
                            cast(
                                DWord_.forceCast(modelStartArray.load(indexExpr))
                            ).expr,
                            true
                        )
                    )
                    val value = Z3Unlogic.undo(
                        model.evaluate(
                            cast(
                                DWord_.forceCast(modelArray.load(indexExpr))
                            ).expr,
                            true
                        )
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

                    val ptrExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr, ef, ctx) as? Ptr_
                        ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }

                    val startV = startMem.load(ptrExpr)
                    val endV = endMem.load(ptrExpr)

                    val modelPtr = Z3Unlogic.undo(model.evaluate(ptrExpr.expr, true))
                    val modelStartV = Z3Unlogic.undo(model.evaluate(startV.expr, true))
                    val modelEndV = Z3Unlogic.undo(model.evaluate(endV.expr, true))

                    memories.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
                    memories.getValue(memspace).first[modelPtr] = modelStartV
                    memories.getValue(memspace).second[modelPtr] = modelEndV
                    if (ptr.type.isString) {
//                        val modelStartStr = ctx.readInitialStringMemory(ptrExpr, memspace)
//                        val modelStr = ctx.readStringMemory(ptrExpr, memspace)
//                        val startStringVal = Z3Unlogic.undo(model.evaluate(modelStartStr.expr, true))
//                        val stringVal = Z3Unlogic.undo(model.evaluate(modelStr.expr, true))
//                        strings.getOrPut(memspace) { hashMapOf<Term, Term>() to hashMapOf() }
//                        strings.getValue(memspace).first[modelPtr] = startStringVal
//                        strings.getValue(memspace).second[modelPtr] = stringVal
                    } else if (ptr.type.isArray) {
                        val (_, endLength) = properties.recoverProperty(
                            ctx,
                            ptr,
                            memspace,
                            KexInt,
                            model,
                            "length"
                        )
                        var maxLen = endLength.numericValue.toInt()
                        // this is fucked up
                        if (maxLen > maxArrayLength) {
                            log.warn("Reanimated length of an array is too big: $maxLen")
                            maxLen = maxArrayLength
                        }
                        properties[memspace]!!["length"]!!.first[modelPtr] = term { const(maxLen) }
                        properties[memspace]!!["length"]!!.second[modelPtr] = term { const(maxLen) }
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
            val arrayPtrExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr.arrayRef, ef, ctx) as? Ptr_
                ?: unreachable { log.error("Non-ptr expr for pointer $ptr") }
            val indexExpr = Z3Converter(executionContext, noAxioms = true).convert(ptr.index, ef, ctx) as? Int_
                ?: unreachable { log.error("Non integer expr for index in $ptr") }

            val modelPtr = Z3Unlogic.undo(model.evaluate(arrayPtrExpr.expr, true))
            val modelIndex = Z3Unlogic.undo(model.evaluate(indexExpr.expr, true))

            val modelStartArray = ctx.readArrayInitialMemory(arrayPtrExpr, memspace)
            val modelArray = ctx.readArrayMemory(arrayPtrExpr, memspace)

            val cast = { arrayVal: DWord_ ->
                when (Z3ExprFactory.getTypeSize((ptr.arrayRef.type as KexArray).element)) {
                    TypeSize.WORD -> Word_.forceCast(arrayVal)
                    TypeSize.DWORD -> arrayVal
                }
            }
            val initialValue = Z3Unlogic.undo(
                model.evaluate(
                    cast(
                        DWord_.forceCast(modelStartArray.load(indexExpr))
                    ).expr,
                    true
                )
            )
            val value = Z3Unlogic.undo(
                model.evaluate(
                    cast(
                        DWord_.forceCast(modelArray.load(indexExpr))
                    ).expr,
                    true
                )
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

    override suspend fun isReachableAsync(state: PredicateState): Result = isReachable(state)

    override suspend fun isPathPossibleAsync(state: PredicateState, path: PredicateState): Result =
        isPathPossible(state, path)

    override suspend fun isViolatedAsync(state: PredicateState, query: PredicateState): Result =
        isViolated(state, query)

    override fun isSatisfiable(state: PredicateState, queries: List<PredicateQuery>): List<Result> {
        if (logQuery) {
            log.run {
                debug("Z3 solver check")
                debug("State: $state")
                debug("Queries: ${queries.joinToString("\n")}")
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

        val ctx = Z3Context(ef)
        val converter = Z3Converter(executionContext)
        val z3State = converter.convert(state, ef, ctx)
        val z3Queries = queries.map { (hard, soft) ->
            converter.convert(hard, ef, ctx) to soft.map { converter.convert(it, ef, ctx) }
        }

        log.debug("Check started")
        val results = checkIncremental(z3State, z3Queries)
        log.debug("Check finished")
        return results.mapIndexed { index, (status, any) ->
            when (status) {
                Status.UNSATISFIABLE -> Result.UnsatResult()
                Status.UNKNOWN -> Result.UnknownResult(any as String)
                Status.SATISFIABLE -> Result.SatResult(
                    collectModel(
                        ctx,
                        any as Model,
                        state + queries[index].hardConstraints
                    )
                )
            }
        }
    }

    private fun checkIncremental(state: Bool_, queries: List<Pair<Bool_, List<Bool_>>>): List<Pair<Status, Any>> {
        val solver = buildSolver()

        val (simplifiedState, simplifiedQueries) = when {
            simplifyFormulae -> state.simplify() to queries.map { it.first.simplify() to it.second }
            else -> state to queries
        }

        if (logFormulae) {
            log.run {
                debug("State: $simplifiedState")
                debug("Query: ${simplifiedQueries.joinToString("\n")}")
            }
        }

        solver.assertAndTrack(simplifiedState.asAxiom() as BoolExpr, ef.ctx.mkBoolConst("State"))
        solver.assertAndTrack(ef.buildConstClassAxioms().asAxiom() as BoolExpr, ef.ctx.mkBoolConst("ClassAxioms"))
        solver.push()

        return simplifiedQueries.map { (hardConstraints, softConstraints) ->
            solver.assertAndTrack(hardConstraints.asAxiom() as BoolExpr, ef.ctx.mkBoolConst("HardConstraints"))
            val softConstraintsMap = when {
                softConstraints.isNotEmpty() -> {
                    solver.push()
                    softConstraints.withIndex().associate { (index, softConstraint) ->
                        val expr = ef.ctx.mkBoolConst("SoftConstraint$index")
                        solver.assertAndTrack(softConstraint.asAxiom() as BoolExpr, expr)
                        expr to softConstraint.asAxiom() as BoolExpr
                    }
                }

                else -> emptyMap()
            }

            log.debug("Running z3 solver")
            if (printSMTLib) {
                log.debug("SMTLib formula:")
                log.debug(solver)
            }
            val result = solver.checkAndMinimize(softConstraintsMap)

            when (result) {
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
            }.also {
                solver.pop()
                if (softConstraints.isNotEmpty()) {
                    solver.pop()
                }
                solver.push()
            }
        }
    }

    private fun com.microsoft.z3.Solver.checkAndMinimize(softConstraintsMap: Map<BoolExpr, BoolExpr>): Status {
        var result = this.check() ?: unreachable { log.error("Solver error") }
        return when (result) {
            Status.UNSATISFIABLE -> {
                while (result == Status.UNSATISFIABLE && softConstraintsMap.isNotEmpty()) {
                    val softCopies = softConstraintsMap.toMutableMap()
                    val core = this.unsatCore.toSet()
                    if (core.any { it !in softCopies }) break
                    else {
                        this.pop()
                        this.push()
                        for (key in core) softCopies.remove(key)
                        for ((expr, softConstraint) in softCopies) {
                            this.assertAndTrack(softConstraint, expr)
                        }
                        result = this.check() ?: unreachable { log.error("Solver error") }
                    }
                }
                result
            }

            else -> result
        }
    }

    override suspend fun isSatisfiableAsync(state: PredicateState, queries: List<PredicateQuery>) =
        isSatisfiable(state, queries)

    override fun close() {
        ef.ctx.close()
    }
}
