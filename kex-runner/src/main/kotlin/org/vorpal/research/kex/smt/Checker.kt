package org.vorpal.research.kex.smt

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.annotations.AnnotationManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.emptyState
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ValueTerm
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kthelper.logging.log

class Checker(
    val method: Method,
    val ctx: ExecutionContext,
    private val psa: PredicateStateAnalysis
) {
    private val isInliningEnabled = kexConfig.getBooleanValue("smt", "psInlining", true)
    private val isMemspacingEnabled = kexConfig.getBooleanValue("smt", "memspacing", true)
    private val isSlicingEnabled = kexConfig.getBooleanValue("smt", "slicing", false)
    private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
    private val annotationsEnabled = kexConfig.getBooleanValue("annotations", "enabled", false)

    private val loader get() = ctx.loader
    private val builder get() = psa.builder(method)
    lateinit var state: PredicateState
        private set
    lateinit var query: PredicateState
        private set

    fun createState(block: BasicBlock) = createState(block.terminator)
    fun createState(inst: Instruction) = builder.getInstructionState(inst)

    fun checkReachable(inst: Instruction): Result {
        log.debug("Checking reachability of ${inst.print()}")

        val state = createState(inst)
            ?: return Result.UnknownResult("Can't get state for instruction ${inst.print()}")
        return prepareAndCheck(state)
    }

    fun prepareState(ps: PredicateState) = transform(ps) {
        +KexRtAdapter(ctx.cm)
        +StringMethodAdapter(ctx.cm)
        if (annotationsEnabled) {
            +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        }
        +RecursiveInliner(psa) { index, psa ->
            ConcreteImplInliner(method.cm.type, TypeInfoMap(), psa, inlineIndex = index)
        }
        +StaticFieldInliner(ctx, psa)
        +ClassAdapter(ctx.cm)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +ReflectionInfoAdapter(method, loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
        +ClassMethodAdapter(method.cm)
        +ConstEnumAdapter(ctx)
        +ConstStringAdapter(method.cm.type)
        +FieldNormalizer(method.cm)
        +TypeNameAdapter(ctx)
    }

    fun prepareState(method: Method, ps: PredicateState, typeInfoMap: TypeInfoMap) = transform(ps) {
        +KexRtAdapter(ctx.cm)
        +StringMethodAdapter(ctx.cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +RecursiveInliner(psa) { index, psa ->
            ConcreteImplInliner(method.cm.type, typeInfoMap, psa, inlineIndex = index)
        }
        +StaticFieldInliner(ctx, psa)
        +ClassAdapter(ctx.cm)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +ReflectionInfoAdapter(method, loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ConstEnumAdapter(ctx)
        +ConstStringAdapter(method.cm.type)
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
        +FieldNormalizer(method.cm)
        +TypeNameAdapter(ctx)
    }

    fun prepareAndCheck(ps: PredicateState): Result {
        val state = prepareState(ps)
        return check(state, state.path)
    }

    fun prepareAndCheck(ps: PredicateState, qry: PredicateState) = check(prepareState(ps), qry)

    fun check(ps: PredicateState, qry: PredicateState = emptyState()): Result {
        state = ps
        query = qry
        if (logQuery) log.debug("State: $state")

        if (isMemspacingEnabled) {
            log.debug("Memspacing started...")
            val spacer = MemorySpacer(state)
            state = spacer.apply(state)
            query = spacer.apply(query)
            log.debug("Memspacing finished")
        }

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

        val result = SMTProxySolver(ctx).use {
            it.isPathPossible(state, query)
        }
        log.debug("Acquired $result")
        return result
    }
}
