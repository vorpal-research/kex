package org.jetbrains.research.kex.smt

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.term.ArgumentTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.ValueTerm
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

class Checker(val method: Method, val loader: ClassLoader, private val psa: PredicateStateAnalysis) {
    private val isInliningEnabled = kexConfig.getBooleanValue("smt", "ps-inlining", true)
    private val isMemspacingEnabled = kexConfig.getBooleanValue("smt", "memspacing", true)
    private val isSlicingEnabled = kexConfig.getBooleanValue("smt", "slicing", false)
    private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)
    private val annotationsEnabled = kexConfig.getBooleanValue("annotations", "enabled", false)

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
        if (annotationsEnabled) {
            +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        }

        if (isInliningEnabled) {
            +MethodInliner(psa)
        }

        +IntrinsicAdapter
        +ReflectionInfoAdapter(method, loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ConstStringAdapter()
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
    }

    fun prepareState(method: Method, ps: PredicateState, typeInfoMap: TypeInfoMap) = transform(ps) {
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +RecursiveInliner(psa) { ConcreteImplInliner(method.cm.type, typeInfoMap, psa, it) }
        +IntrinsicAdapter
        +ReflectionInfoAdapter(method, loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ConstStringAdapter()
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
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
            log.debug("Query: $query")
        }

        val result = SMTProxySolver(method.cm.type).use {
            it.isPathPossible(state, query)
        }
        log.debug("Acquired $result")
        return result
    }
}