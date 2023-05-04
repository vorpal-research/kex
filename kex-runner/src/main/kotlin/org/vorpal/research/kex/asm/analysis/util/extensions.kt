package org.vorpal.research.kex.asm.analysis.util

import kotlinx.coroutines.TimeoutCancellationException
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.crash.precondition.ConstraintExceptionPrecondition
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexRtManager.isJavaRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.concreteParameters
import org.vorpal.research.kex.parameters.filterIgnoredStatic
import org.vorpal.research.kex.parameters.filterStaticFinals
import org.vorpal.research.kex.smt.AsyncChecker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.SymbolicStateSlicer
import org.vorpal.research.kex.state.transformer.collectArguments
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

suspend fun Method.checkAsync(ctx: ExecutionContext, state: SymbolicState): Parameters<Descriptor>? {
    val checker = AsyncChecker(this, ctx)
    val clauses = state.clauses.asState()
    val query = state.path.asState()
    val concreteTypeInfo = state.concreteValueMap
        .mapValues { it.value.type }
        .filterValues { it.isJavaRt }
        .mapValues { it.value.rtMapped }
        .toTypeMap()
    val result = checker.prepareAndCheck(this, clauses + query, concreteTypeInfo)
    if (result !is Result.SatResult) {
        return null
    }

    return try {
        generateInitialDescriptors(this, ctx, result.model, checker.state)
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

suspend fun Method.checkAsyncAndSlice(
    ctx: ExecutionContext,
    state: SymbolicState
): Pair<Parameters<Descriptor>, ConstraintExceptionPrecondition>? {
    val checker = AsyncChecker(this, ctx)
    val clauses = state.clauses.asState()
    val query = state.path.asState()
    val concreteTypeInfo = state.concreteValueMap
        .mapValues { it.value.type }
        .filterValues { it.isJavaRt }
        .mapValues { it.value.rtMapped }
        .toTypeMap()
    val result = checker.prepareAndCheck(this, clauses + query, concreteTypeInfo)
    if (result !is Result.SatResult) {
        return null
    }

    return try {
        val (params, aa) = generateInitialDescriptorsAndAA(this, ctx, result.model, checker.state)
        val filteredParams = params.concreteParameters(ctx.cm, ctx.accessLevel, ctx.random).also {
            log.debug { "Generated params:\n$it" }
        }
            .filterStaticFinals(ctx.cm)
            .filterIgnoredStatic()

        val (thisTerm, argTerms) = collectArguments(checker.state)
        val termParams = Parameters(thisTerm, this@checkAsyncAndSlice.argTypes.mapIndexed { index, type ->
            argTerms[index] ?: term { arg(type.kexType, index) }
        })

        filteredParams to ConstraintExceptionPrecondition(
            termParams,
            SymbolicStateSlicer(termParams.asList.toSet(), aa).apply(state)
        )
    } catch (e: Throwable) {
        log.error("Error during descriptor generation: ", e)
        null
    }
}
