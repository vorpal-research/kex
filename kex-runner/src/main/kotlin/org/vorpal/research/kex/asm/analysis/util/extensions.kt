package org.vorpal.research.kex.asm.analysis.util

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.TimeoutCancellationException
import org.slf4j.LoggerFactory
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.crash.precondition.ConstraintExceptionPrecondition
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.FullDescriptorContext
import org.vorpal.research.kex.ktype.KexRtManager.isJavaRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.mocking.performMocking
import org.vorpal.research.kex.mocking.withoutMocksOrNull
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.concreteParameters
import org.vorpal.research.kex.parameters.filterIgnoredStatic
import org.vorpal.research.kex.parameters.filterStaticFinals
import org.vorpal.research.kex.smt.AsyncChecker
import org.vorpal.research.kex.smt.AsyncIncrementalChecker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.SymbolicStateForwardSlicer
import org.vorpal.research.kex.state.transformer.collectArguments
import org.vorpal.research.kex.state.transformer.generateFinalDescriptors
import org.vorpal.research.kex.state.transformer.generateInitialDescriptors
import org.vorpal.research.kex.state.transformer.generateInitialDescriptorsAndAA
import org.vorpal.research.kex.state.transformer.toTypeMap
import org.vorpal.research.kex.trace.symbolic.SymbolicState
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

private val statLogger = LoggerFactory.getLogger("StatLogger")

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
    statLogger.debug("SAT Solver: {}", result)
    if (result !is Result.SatResult) {
        return null
    }

    return try {
        generateInitialDescriptors(this, ctx, result.model, checker.state)
            .performMocking(ctx, state, this)
            .parameters
            .filterStaticFinals(ctx.cm)
            .concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
            .also { log.debug { "Generated params:\n$it" } }
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
    val result = checker.prepareAndCheck(this, clauses + query, concreteTypeInfo, enableInlining)
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
        val filteredParams = params
            .performMocking(ctx, state, this)
            .parameters
            .filterStaticFinals(ctx.cm)
            .concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
            .also { log.debug { "Generated params:\n$it" } }
            .filterIgnoredStatic()

        val (thisTerm, argTerms) = collectArguments(checker.state)
        val termParams = Parameters(
            thisTerm,
            this@checkAsyncAndSlice.argTypes.mapIndexed { index, type ->
                argTerms[index] ?: term { arg(type.kexType, index) }
            }
        )

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
): List<FullDescriptorContext?> {
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
                val initialDescriptors = generateInitialDescriptors(this, ctx, result.model, fullPS)
                    .performMocking(ctx, state, this)
                    .transform {
                        it.concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
                            .filterStaticFinals(ctx.cm)
                            .also { log.debug { "Generated params:\n$it" } }
                            .filterIgnoredStatic()
                    }

                when {
                    kexConfig.getBooleanValue("testGen", "generateAssertions", false) -> {
                        val finalDescriptors = generateFinalDescriptors(this, ctx, result.model, fullPS)
                            .withoutMocksOrNull(ctx, this)
                            ?.transform {
                                it.concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
                                    .filterStaticFinals(ctx.cm)
                                    .filterIgnoredStatic()
                            }
                        FullDescriptorContext(initialDescriptors, finalDescriptors)
                    }

                    else -> FullDescriptorContext(initialDescriptors, null)
                }
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
): List<Pair<FullDescriptorContext, ConstraintExceptionPrecondition>?> {
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
                val filteredParams = params
                    .performMocking(ctx, state, this)
                    .transform {
                        it.concreteParameters(ctx.cm, ctx.accessLevel, ctx.random)
                            .filterStaticFinals(ctx.cm)
                            .also { log.debug { "Generated params:\n$it" } }
                            .filterIgnoredStatic()
                    }

                val (thisTerm, argTerms) = collectArguments(fullPS)
                val termParams = Parameters(
                    thisTerm,
                    this@checkAsyncIncrementalAndSlice.argTypes.mapIndexed { i, type ->
                        argTerms[i] ?: term { arg(type.kexType, i) }
                    }
                )

                FullDescriptorContext(filteredParams, null) to ConstraintExceptionPrecondition(
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
