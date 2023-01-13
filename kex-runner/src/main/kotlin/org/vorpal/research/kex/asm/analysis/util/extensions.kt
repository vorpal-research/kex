package org.vorpal.research.kex.asm.analysis.util

import kotlinx.coroutines.TimeoutCancellationException
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexRtManager.isJavaRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.parameters.concreteParameters
import org.vorpal.research.kex.parameters.filterStaticFinals
import org.vorpal.research.kex.smt.AsyncChecker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.transformer.generateInitialDescriptors
import org.vorpal.research.kex.state.transformer.toTypeMap
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.tryOrNull

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

    return tryOrNull {
        generateInitialDescriptors(this, ctx, result.model, checker.state)
            .concreteParameters(ctx.cm, ctx.accessLevel, ctx.random).also {
                log.debug { "Generated params:\n$it" }
            }
            .filterStaticFinals(ctx.cm)
    }
}
