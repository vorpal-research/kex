package org.vorpal.research.kex.asm.analysis.util

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.TimeoutCancellationException
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.crash.precondition.ConstraintExceptionPrecondition
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.KexRtManager.isJavaRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.*
import org.vorpal.research.kex.smt.AsyncChecker
import org.vorpal.research.kex.smt.AsyncIncrementalChecker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn

suspend fun Method.analyzeOrTimeout(
    accessLevel: AccessModifier,
    analysis: suspend (Method) -> Unit
) {
    try {
        if (this.isStaticInitializer || !this.hasBody) return
        if (!MethodManager.canBeImpacted(this, accessLevel)) return

        log.debug { "Processing method $this" }
        log.debug { this.print() }

        analysis(this)
        log.debug { "Method $this processing is finished normally" }
    } catch (e: TimeoutCancellationException) {
        log.warn { "Method $this processing is finished with timeout" }
    }
}

fun CallTerm.replaceOwner(owner: Term): CallTerm {
    return CallTerm(type, owner, method, arguments)
}

fun methodCalls(
    state: SymbolicState,
    termToDescriptor: Map<Term, Descriptor>,
    descriptorToMock: Map<Descriptor, Descriptor>
): List<Pair<CallPredicate, Descriptor>> {
    return state.clauses
        .asSequence()
        .map { clause -> clause.predicate }
        .filter { predicate -> predicate.type.name == "S" }
        .filterIsInstance<CallPredicate>()
        .filter { predicate -> predicate.hasLhv }
        .map { predicate ->
            val term = predicate.lhv
            val descriptor = termToDescriptor[term]
            if (descriptor == null) log.debug { "Error. No descriptor for $term, type: ${term.type}\n" }
            predicate to descriptor
        }
        .filter { (_, value) -> value != null }
        .map { (call, value) -> call to value!! }
        .map { (call, value) -> call to descriptorToMock.getOrElse(value) { value } }
        .toList()
}


fun Descriptor.requireMocks(visited: MutableSet<Descriptor>, types: TypeFactory): Boolean {
    if (this in visited) return false
    if (isMockable(types)) return true
    visited += this

    fun Descriptor.requireMocks(): Boolean = this.requireMocks(visited, types)
    return when (this) {
        is ConstantDescriptor -> false

        is MockDescriptor -> fields.values.any { it.requireMocks() } || allReturns.any { it.requireMocks() }
        is ClassDescriptor -> fields.values.any { it.requireMocks() }
        is ObjectDescriptor -> fields.values.any { it.requireMocks() }
        is ArrayDescriptor -> elements.values.any { it.requireMocks() }
    }
}

suspend fun Method.checkAsync(
    ctx: ExecutionContext,
    state: SymbolicState,
    enableInlining: Boolean = false
): Parameters<Descriptor>? {
    val checker = AsyncChecker(this, ctx)
    val clauses = state.clauses.asState()
    val query = state.path.asState()
    val concreteTypeInfo = state.concreteTypes
        .filterValues { it.isJavaRt }
        .mapValues { it.value.rtMapped }
        .toTypeMap()
    val result = checker.prepareAndCheck(this, clauses + query, concreteTypeInfo, enableInlining)
    if (result !is Result.SatResult) {
        return null
    }


    return try {
        val (initialDescriptors, termToDescriptor) = generateInitialDescriptors(
            this,
            ctx,
            result.model,
            checker.state
        )

        val visited = mutableSetOf<Descriptor>()
        val finalDescriptors = if (initialDescriptors.asList.any { it.requireMocks(visited, ctx.types) }) {
            val (withMocks, descriptorToMock) = initialDescriptors.generateInitialMocks(ctx.types)
            if (descriptorToMock.isNotEmpty()) {
                val methodCalls = methodCalls(state, termToDescriptor, descriptorToMock)
                setupMocks(methodCalls, termToDescriptor, descriptorToMock)
            }
            withMocks
        } else {
            initialDescriptors
        }

        finalDescriptors
            .concreteParameters(ctx.cm, ctx.accessLevel, ctx.random).also {
                log.debug { "Generated params:\n$it" }
            }
            .filterStaticFinals(ctx.cm)
            .filterIgnoredStatic()
    } catch (e: Throwable) {
        log.error("Error during descriptor generation: ", e)
        null
    }
}

@Suppress("unused")
suspend fun Method.checkAsyncAndSlice(
    ctx: ExecutionContext,
    state: SymbolicState,
    enableInlining: Boolean = false
): Pair<Parameters<Descriptor>, ConstraintExceptionPrecondition>? {
    val checker = AsyncChecker(this, ctx)
    val clauses = state.clauses.asState()
    val query = state.path.asState()
    val concreteTypeInfo = state.concreteTypes
        .filterValues { it.isJavaRt }
        .mapValues { it.value.rtMapped }
        .toTypeMap()
    val result =
        checker.prepareAndCheck(this, clauses + query, concreteTypeInfo, enableInlining)
    if (result !is Result.SatResult) {
        return null
    }

    return try {
        val (params, aa) = generateInitialDescriptorsAndAA(
            this,
            ctx,
            result.model,
            checker.state
        )
        val filteredParams =
            params.concreteParameters(ctx.cm, ctx.accessLevel, ctx.random).also {
                log.debug { "Generated params:\n$it" }
            }
                .filterStaticFinals(ctx.cm)
                .filterIgnoredStatic()

        val (thisTerm, argTerms) = collectArguments(checker.state)
        val termParams =
            Parameters(thisTerm, this@checkAsyncAndSlice.argTypes.mapIndexed { index, type ->
                argTerms[index] ?: term { arg(type.kexType, index) }
            })

        filteredParams to ConstraintExceptionPrecondition(
            termParams,
            SymbolicStateForwardSlicer(termParams.asList.toSet(), aa).apply(state)
        )
    } catch (e: Throwable) {
        log.error("Error during descriptor generation: ", e)
        null
    }
}


suspend fun Method.checkAsyncIncremental(
    ctx: ExecutionContext,
    state: SymbolicState,
    queries: List<SymbolicState>,
    enableInlining: Boolean = false
): List<Parameters<Descriptor>?> {
    val checker = AsyncIncrementalChecker(this, ctx)
    val clauses = state.clauses.asState()
    val query = state.path.asState()
    val concreteTypeInfo = state.concreteTypes
        .filterValues { it.isJavaRt }
        .mapValues { it.value.rtMapped }
        .toTypeMap()

    val results = checker.prepareAndCheck(
        this,
        IncrementalPredicateState(
            clauses + query,
            queries.map { PredicateQuery(it.clauses.asState() + it.path.asState()) }
                .toPersistentList()
        ),
        concreteTypeInfo,
        enableInlining
    )

    return results.mapIndexed { index, result ->
        when (result) {
            is Result.SatResult -> try {
                val fullPS = checker.state + checker.queries[index].hardConstraints
                generateInitialDescriptors(this, ctx, result.model, fullPS).first
                    .concreteParameters(ctx.cm, ctx.accessLevel, ctx.random).also {
                        log.debug { "Generated params:\n$it" }
                    }
                    .filterStaticFinals(ctx.cm)
                    .filterIgnoredStatic()
            } catch (e: Throwable) {
                log.error("Error during descriptor generation: ", e)
                null
            }

            else -> null
        }
    }
}


@Suppress("unused", "unused")
suspend fun Method.checkAsyncIncrementalAndSlice(
    ctx: ExecutionContext,
    state: SymbolicState,
    queries: List<SymbolicState>,
    enableInlining: Boolean = false
): List<Pair<Parameters<Descriptor>, ConstraintExceptionPrecondition>?> {
    val checker = AsyncIncrementalChecker(this, ctx)
    val clauses = state.clauses.asState()
    val query = state.path.asState()
    val concreteTypeInfo = state.concreteTypes
        .filterValues { it.isJavaRt }
        .mapValues { it.value.rtMapped }
        .toTypeMap()

    val results = checker.prepareAndCheck(
        this,
        IncrementalPredicateState(
            clauses + query,
            queries.map { PredicateQuery(it.clauses.asState() + it.path.asState()) }
                .toPersistentList()
        ),
        concreteTypeInfo,
        enableInlining
    )

    return results.mapIndexed { index, result ->
        when (result) {
            is Result.SatResult -> try {
                val fullPS = (checker.state + checker.queries[index].hardConstraints).simplify()
                val (params, aa) = generateInitialDescriptorsAndAA(
                    this,
                    ctx,
                    result.model,
                    fullPS
                )
                val filteredParams =
                    params.concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
                        .also { log.debug { "Generated params:\n$it" } }
                        .filterStaticFinals(ctx.cm)
                        .filterIgnoredStatic()

                val (thisTerm, argTerms) = collectArguments(fullPS)
                val termParams = Parameters(
                    thisTerm,
                    this@checkAsyncIncrementalAndSlice.argTypes.mapIndexed { i, type ->
                        argTerms[i] ?: term { arg(type.kexType, i) }
                    }
                )

                filteredParams to ConstraintExceptionPrecondition(
                    termParams,
                    SymbolicStateForwardSlicer(
                        termParams.asList.toSet(),
                        aa
                    ).apply(state + queries[index])
                )
            } catch (e: Throwable) {
                log.error("Error during descriptor generation: ", e)
                null
            }

            else -> null
        }
    }
}
