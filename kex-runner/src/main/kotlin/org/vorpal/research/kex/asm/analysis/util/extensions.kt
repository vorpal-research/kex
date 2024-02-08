package org.vorpal.research.kex.asm.analysis.util

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.TimeoutCancellationException
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.crash.precondition.ConstraintExceptionPrecondition
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.config.kexConfig
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
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.ir.Method
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


fun SymbolicState.methodCalls(): List<CallPredicate> {
    return clauses.map { clause -> clause.predicate }.filterIsInstance<CallPredicate>()
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
    val result =
        checker.prepareAndCheck(this, clauses + query, concreteTypeInfo, enableInlining)
    if (result !is Result.SatResult) {
        return null
    }


    return try {
        val (initialDescriptors, generator) = generateInitialDescriptors(
            this,
            ctx,
            result.model,
            checker.state
        )

        val finalDescriptors = initialDescriptors.finalizeDescriptors(ctx, generator, state)

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

private fun Parameters<Descriptor>.finalizeDescriptors(
    ctx: ExecutionContext,
    generator: DescriptorGenerator,
    state: SymbolicState
): Parameters<Descriptor> {
    if (!kexConfig.isMockingEnabled || kexConfig.mockingMode == null) {
        return this
    }
    fun Collection<Descriptor>.removeInstance() = this.filterNot { it == instance }
    val visited = mutableSetOf<Descriptor>()

    if (kexConfig.isMockPessimizationEnabled) {
        if (this.asList.removeInstance().none { it.requireMocks(ctx.types, visited) }) {
            return this
        }
    }
    generator.generateAll()
    if (generator.allValues.removeInstance().none { it.requireMocks(ctx.types, visited) }) {
        return this
    }

    val descriptorToMock = createDescriptorToMock(generator.allValues.removeInstance(), ctx.types)
    val withMocks = this.map { descriptor -> descriptorToMock[descriptor] ?: descriptor }
    val methodCalls = state.methodCalls()
    setupMocks(methodCalls, generator.memory, descriptorToMock)
    return withMocks
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
                generateInitialDescriptors(this, ctx, result.model, fullPS)
                    .let { (descriptors, generator) ->
                        descriptors.finalizeDescriptors(ctx, generator, state)
                    }
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
