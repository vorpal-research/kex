package org.vorpal.research.kex.smt

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.annotations.AnnotationManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.emptyState
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ValueTerm
import org.vorpal.research.kex.state.transformer.AnnotationAdapter
import org.vorpal.research.kex.state.transformer.BasicInvariantsTransformer
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
import org.vorpal.research.kex.state.transformer.ReflectionInfoAdapter
import org.vorpal.research.kex.state.transformer.Slicer
import org.vorpal.research.kex.state.transformer.StaticFieldWDescriptorInliner
import org.vorpal.research.kex.state.transformer.StensgaardAA
import org.vorpal.research.kex.state.transformer.StringMethodAdapter
import org.vorpal.research.kex.state.transformer.TermCollector
import org.vorpal.research.kex.state.transformer.TypeInfoMap
import org.vorpal.research.kex.state.transformer.TypeNameAdapter
import org.vorpal.research.kex.state.transformer.collectRequiredTerms
import org.vorpal.research.kex.state.transformer.collectVariables
import org.vorpal.research.kex.state.transformer.domain.tryAbstractDomainSolve
import org.vorpal.research.kex.state.transformer.toTypeMap
import org.vorpal.research.kex.state.transformer.transform
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log

@Suppress("MemberVisibilityCanBePrivate")
class AsyncChecker(
    val method: Method,
    val ctx: ExecutionContext,
) {
    private val isSlicingEnabled = kexConfig.getBooleanValue("smt", "slicing", false)
    private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
    private val useADSolver = kexConfig.getBooleanValue("smt", "useADSolver", false)
    private val psa = PredicateStateAnalysis(ctx.cm)

    lateinit var state: PredicateState
        private set
    lateinit var query: PredicateState
        private set

    fun prepareState(
        method: Method,
        state: PredicateState,
        typeMap: TypeInfoMap,
        enableInlining: Boolean
    ): PredicateState = transform(state) {
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
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +BasicInvariantsTransformer(method)
        +ReflectionInfoAdapter(method, ctx.loader)
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
        state: PredicateState,
        typeMap: TypeInfoMap = emptyMap<Term, KexType>().toTypeMap(),
        enableInlining: Boolean = false
    ): Result {
        val preparedState = prepareState(method, state, typeMap, enableInlining)
        log.debug { "Prepared state: $preparedState" }
        return check(preparedState)
    }

    suspend fun check(ps: PredicateState, qry: PredicateState = emptyState()): Result {
        state = ps
        query = qry
        if (logQuery) log.debug("State: {}", state)

        if (isSlicingEnabled) {
            log.debug("Slicing started...")

            val variables = collectVariables(state)
            val slicingTerms = run {
                val `this` = variables.find { it is ValueTerm && it.name == "this" }

                val results = hashSetOf<Term>()
                if (`this` != null) results += `this`

                results += variables.filterIsInstance<ArgumentTerm>()
                results += variables.filter { it is FieldTerm && it.owner == `this` }
                results += collectRequiredTerms(state)
                results += TermCollector.getFullTermSet(query)
                results
            }

            val aa = StensgaardAA()
            aa.apply(state)
            log.debug("State size before slicing: ${state.size}")
            state = Slicer(state, query, slicingTerms, aa).apply(state)
            log.debug("State size after slicing: ${state.size}")
            log.debug("Slicing finished")
        }

        state = Optimizer().apply(state)
        query = Optimizer().apply(query)
        if (logQuery) {
            log.debug("Simplified state: {}", state)
            log.debug("State size: {}", state.size)
            log.debug("Query: {}", query)
            log.debug("Query size: {}", query.size)
        }

        if (useADSolver) {
            tryAbstractDomainSolve(ctx, state, query)?.let {
                log.debug("Constant solver acquired {}", it)
                return it
            }
        }

        val result = AsyncSMTProxySolver(ctx).use {
            it.isPathPossibleAsync(state, query)
        }
        log.debug("Acquired {}", result)
        return result
    }
}
