package org.vorpal.research.kex.smt

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.BoolTypeAdapter
import org.vorpal.research.kex.state.transformer.ClassAdapter
import org.vorpal.research.kex.state.transformer.ClassMethodAdapter
import org.vorpal.research.kex.state.transformer.ConcolicArrayLengthAdapter
import org.vorpal.research.kex.state.transformer.ConcolicInliner
import org.vorpal.research.kex.state.transformer.ConstEnumAdapter
import org.vorpal.research.kex.state.transformer.ConstStringAdapter
import org.vorpal.research.kex.state.transformer.ConstantPropagator
import org.vorpal.research.kex.state.transformer.EqualsTransformer
import org.vorpal.research.kex.state.transformer.FieldNormalizer
import org.vorpal.research.kex.state.transformer.IntrinsicAdapter
import org.vorpal.research.kex.state.transformer.KexIntrinsicsAdapter
import org.vorpal.research.kex.state.transformer.KexRtAdapter
import org.vorpal.research.kex.state.transformer.NewFieldInitializer
import org.vorpal.research.kex.state.transformer.Optimizer
import org.vorpal.research.kex.state.transformer.RecursiveInliner
import org.vorpal.research.kex.state.transformer.StaticFieldWDescriptorInliner
import org.vorpal.research.kex.state.transformer.StringMethodAdapter
import org.vorpal.research.kex.state.transformer.TypeInfoMap
import org.vorpal.research.kex.state.transformer.TypeNameAdapter
import org.vorpal.research.kex.state.transformer.toTypeMap
import org.vorpal.research.kex.state.transformer.transformIncremental
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log

class AsyncIncrementalChecker(
    val method: Method,
    val ctx: ExecutionContext,
) {
    private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
    private val psa = PredicateStateAnalysis(ctx.cm)

    lateinit var state: PredicateState
        private set
    lateinit var queries: List<PredicateQuery>
        private set

    private fun prepareState(
        method: Method,
        state: IncrementalPredicateState,
        typeMap: TypeInfoMap,
        enableInlining: Boolean
    ): IncrementalPredicateState = transformIncremental(state) {
        +KexRtAdapter(ctx.cm)
        if (enableInlining) {
            +RecursiveInliner(psa) { index, psa ->
                ConcolicInliner(
                    ctx,
                    typeMap,
                    psa,
                    inlineSuffix = "inlined",
                    inlineIndex = index,
                    kexRtOnly = false
                )
            }
            +RecursiveInliner(psa) { index, psa ->
                ConcolicInliner(
                    ctx,
                    typeMap,
                    psa,
                    inlineSuffix = "rt.inlined",
                    inlineIndex = index,
                    kexRtOnly = true
                )
            }
        }
        +ClassAdapter(ctx.cm)
//        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ClassMethodAdapter(method.cm)
        +ConstEnumAdapter(ctx)
        +StaticFieldWDescriptorInliner(ctx)
        +ConstStringAdapter(method.cm.type)
        +StringMethodAdapter(ctx.cm)
        +ConcolicArrayLengthAdapter()
        +NewFieldInitializer(ctx)
        +FieldNormalizer(method.cm)
        +TypeNameAdapter(ctx)
    }

    suspend fun prepareAndCheck(
        method: Method,
        state: IncrementalPredicateState,
        typeMap: TypeInfoMap = emptyMap<Term, KexType>().toTypeMap(),
        enableInlining: Boolean = false
    ): List<Result> {
        val preparedState = prepareState(method, state, typeMap, enableInlining)
        log.debug { "Prepared state: $preparedState" }
        return check(preparedState.state, preparedState.queries)
    }

    suspend fun check(
        ps: PredicateState,
        query: List<PredicateQuery>
    ): List<Result> {
        state = ps
        queries = query
        if (logQuery) {
            log.debug("State: {}", state)
            log.debug("Queries: {}", queries)
        }

        state = Optimizer().apply(state)
        queries = Optimizer().let { opt ->
            queries.map { (hardConstraints, softConstraints) ->
                PredicateQuery(
                    opt.apply(hardConstraints),
                    softConstraints
                )
            }
        }
        if (logQuery) {
            log.debug("Simplified state: {}", state)
            log.debug("State size: {}", state.size)
            log.debug("Query: {}", query)
            log.debug("Query size: {}", query.size)
        }

        val results = AsyncIncrementalSMTProxySolver(ctx).use {
            it.isSatisfiableAsync(state, queries)
        }
        log.debug("Acquired {}", results)
        return results
    }
}
