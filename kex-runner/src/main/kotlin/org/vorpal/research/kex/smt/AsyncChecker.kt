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
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log

class AsyncChecker(
    val method: Method,
    val ctx: ExecutionContext,
) {
    private val isSlicingEnabled = kexConfig.getBooleanValue("smt", "slicing", false)
    private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
    private val psa = PredicateStateAnalysis(ctx.cm)

    private val loader get() = ctx.loader
    lateinit var state: PredicateState
        private set
    lateinit var query: PredicateState
        private set

    private fun prepareState(
        method: Method,
        state: PredicateState,
        typeMap: TypeInfoMap
    ): PredicateState = transform(state) {
        +KexRtAdapter(ctx.cm)
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
        +ClassAdapter(ctx.cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +ReflectionInfoAdapter(method, ctx.loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ClassMethodAdapter(method.cm)
        +ConstEnumAdapter(ctx)
        +ConstStringAdapter(method.cm.type)
        +StringMethodAdapter(ctx.cm)
        +ConcolicArrayLengthAdapter()
        +FieldNormalizer(method.cm)
        +TypeNameAdapter(ctx.types)
    }

    suspend fun prepareAndCheck(
        method: Method,
        state: PredicateState,
        typeMap: TypeInfoMap = emptyMap<Term, KexType>().toTypeMap()
    ): Result {
        val preparedState = prepareState(method, state, typeMap)
        log.debug { "Prepared state: $preparedState" }
        return check(preparedState)
    }

    suspend fun check(ps: PredicateState, qry: PredicateState = emptyState()): Result {
        state = ps
        query = qry
        if (logQuery) log.debug("State: $state")

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
            log.debug("Simplified state: $state")
            log.debug("State size: ${state.size}")
            log.debug("Query: $query")
            log.debug("Query size: ${query.size}")
        }

        val result = AsyncSMTProxySolver(method.cm.type).use {
            it.isPathPossibleAsync(state, query)
        }
        log.debug("Acquired $result")
        return result
    }
}
