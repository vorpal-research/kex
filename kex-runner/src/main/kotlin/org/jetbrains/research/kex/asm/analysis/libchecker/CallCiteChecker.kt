package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.analysis.defect.Defect
import org.jetbrains.research.kex.asm.analysis.defect.DefectManager
import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.SMTProxySolver
import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.require
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.StringConstant
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.runIf

private val logQuery by lazy { kexConfig.getBooleanValue("smt", "logQuery", false) }
private val isMemspacingEnabled by lazy { kexConfig.getBooleanValue("smt", "memspacing", true) }
private val isSlicingEnabled by lazy { kexConfig.getBooleanValue("smt", "slicing", false) }

class CallCiteChecker(
        val ctx: ExecutionContext,
        private val callCiteTarget: Package,
        val psa: PredicateStateAnalysis
) : MethodVisitor {
    override val cm: ClassManager
        get() = ctx.cm
    private val dm get() = DefectManager
    private val im get() = MethodManager.KexIntrinsicManager
    private lateinit var method: Method
    private val methodCallGraph = mutableMapOf<Method, Set<CallInst>>()

    override fun cleanup() {}

    override fun visit(method: Method) {
        this.method = method

        super.visit(method)
    }

    override fun visitCallInst(inst: CallInst) {
        val allAssertions = when (inst.method) {
            im.kexAssert(cm) -> getAllAssertions(inst.args[0])
            im.kexAssertWithId(cm) -> getAllAssertions(inst.args[1])
            else -> return
        }

        val allPredicateStates = getPredicateStatesAndAssertions(inst, allAssertions)
        for ((predicateState, callStack, assertions) in allPredicateStates) {
            when (inst.method) {
                im.kexAssert(cm) -> checkAssertion(inst, callStack, predicateState, assertions)
                im.kexAssertWithId(cm) -> {
                    val id = (inst.args[0] as? StringConstant)?.value
                    checkAssertion(inst, callStack, predicateState, assertions, id)
                }
                else -> {}
            }
        }
    }

    infix fun Method.overrides(other: Method): Boolean = when {
        this == other -> true
        other.isFinal -> false
        this.klass !is ConcreteClass -> false
        other.klass !is ConcreteClass -> false
        this.name != other.name -> false
        this.desc != other.desc -> false
        !this.klass.isInheritorOf(other.klass) -> false
        else -> true
    }

    private fun getState(instruction: Instruction) =
        psa.builder(instruction.parent.parent).getInstructionState(instruction)

    private fun PredicateState.lastPredicate(): Predicate? = when (this) {
        is BasicState -> this.lastOrNull()
        is ChainState -> curr.lastPredicate()
        else -> null
    }

    private fun inlineState (
        main: PredicateState,
        inline: PredicateState
    ): Pair<PredicateState, TermRenamer>? {
        val callPredicate = main.lastPredicate() ?: return null
        val filteredState = main.dropLast(1)
        if (callPredicate !is CallPredicate) {
            log.warn("Unknown predicate in call cite: $callPredicate")
            return null
        }
        val callTerm = callPredicate.callTerm as CallTerm
        val (inlinedThis, inlinedArgs) = collectArguments(inline)
        val mappings = run {
            val result = mutableMapOf<Term, Term>()
            if (inlinedThis != null && callTerm.method.klass.toType().kexType == callTerm.owner.type) {  // todo this isn't correct
                result += inlinedThis to callTerm.owner
            } else if (!method.isStatic) {
                result += term { `this`(method.klass.kexType) } to callTerm.owner
            }
            for ((index, arg) in callTerm.arguments.withIndex()) {
                result += (inlinedArgs[index] ?: term { arg(arg.type, index) }) to arg
            }
            result
        }
        val remapper =  TermRenamer("call.cite.inlined", mappings)
        val preparedState = remapper.apply(inline)
        return (filteredState + preparedState) to remapper
    }

    private fun getAllAssertions(assertionsArray: Value): Set<Term> = method.flatten()
        .asSequence()
        .mapNotNull { it as? ArrayStoreInst }
        .filter { it.arrayRef == assertionsArray }
        .map { it.value }
        .map { term { value(it) } }
        .toSet()

    private fun getPredicateStatesAndAssertions(inst: Instruction, _assertions: Set<Term>): MutableSet<PredicateStateWithInfo> {
        val method = inst.parent.parent

        val result = mutableSetOf<PredicateStateWithInfo>()
        for (callCite in getAllCallCites(method)) {
            val callChains = getFullCallChain(listOf(callCite)).map { chain ->
                var assertions = _assertions

                val init = getState(inst) ?: emptyState()
                val inlinedChain = chain.fold(init) { acc, inst ->
                    val (inlined, renamer) = inlineState(getState(inst) ?: emptyState(), acc) ?: emptyState() to null
                    assertions = assertions.map { renamer?.transform(it) ?: it }.toSet()
                    inlined
                }
                PredicateStateWithInfo(
                        inlinedChain,
                        chain.reversed(),
                        assertions
                )
            }
            result.addAll(callChains)
        }

        return result
    }

    private fun getFullCallChain(chain: CallInstChain): List<CallInstChain> {
        val result = mutableListOf<CallInstChain>()
        val callCites = getAllCallCites(chain.last().parent.parent)
        for (callCite in callCites) {
            if (callCite in chain) continue

            val newChain = chain.plusElement(callCite)
            val newChains = getFullCallChain(newChain)
            if (newChain.isNotEmpty()) {
                result.addAll(newChains)
            } else {
                result.add(newChain)
            }
        }

        if (callCites.isEmpty()) {
            result.add(chain)
        }

        return result
    }

    private fun getAllCallCites(method: Method): Set<CallInst> {
        methodCallGraph[method]?.let {
            return it
        }
        val result = mutableSetOf<CallInst>()
        executePipeline(cm, callCiteTarget) {
            +object : MethodVisitor {
                override val cm: ClassManager
                    get() = this@CallCiteChecker.cm

                override fun cleanup() {}

                override fun visitCallInst(inst: CallInst) {
                    val calledMethod = inst.method
                    if (calledMethod overrides method)
                        result += inst
                }
            }
        }
        methodCallGraph[method] = result
        return result
    }

    private fun checkAssertion(
        inst: Instruction,
        callStack: List<CallInst>,
        state: PredicateState,
        assertions: Set<Term>,
        id: String? = null
    ): Boolean {
        val preparedCallStack = callStack.callStack
        log.debug("Checking for assertion failure: ${inst.print()} at \n$preparedCallStack")
        log.debug("State: $state")
        val assertionQuery = assertions
                .map { require { it equality true } }
                .fold(StateBuilder()) { builder, predicate ->
            builder += predicate
            builder
        }.apply()

        val (_, result) = check(state, assertionQuery)
        return when (result) {
            is Result.SatResult -> {
                dm += Defect.assert(callStack.callStack, id)
                false
            }
            else -> true
        }
    }

    private val List<CallInst>.callStack: List<String>
        get() = this.map { "${it.method} - ${it.location}\n" }

    private fun prepareState(ps: PredicateState, typeInfoMap: TypeInfoMap) = transform(ps) {
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +RecursiveInliner(psa) { i, psa -> ConcreteImplInliner(method.cm.type, typeInfoMap, psa, inlineIndex = i) }
        +StaticFieldInliner(ctx, psa)
        +RecursiveInliner(psa) { i, psa -> MethodInliner(psa, inlineIndex = i) }
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +DoubleTypeAdapter()
        +ReflectionInfoAdapter(method, ctx.loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ConstStringAdapter()
        +FieldNormalizer(method.cm)
    }

    private fun check(state_: PredicateState, query_: PredicateState): Pair<PredicateState, Result> {
        val staticTypeInfoMap = collectStaticTypeInfo(types, state_, TypeInfoMap())
        var state = prepareState(state_, staticTypeInfoMap)
        var query = query_

        // memspacing
        runIf(isMemspacingEnabled) {
            log.debug("Memspacing started...")
            val spacer = MemorySpacer((state.builder() + query).apply())
            state = spacer.apply(state)
            query = spacer.apply(query)
            log.debug("Memspacing finished")
        }

        // slicing
        runIf(isSlicingEnabled) {
            log.debug("Slicing started...")

            val slicingTerms = run {
                val (`this`, arguments) = collectArguments(state)

                val results = hashSetOf<Term>()

                if (`this` != null) results += `this`
                results += arguments.values
                results += collectVariables(state).filter { it is FieldTerm && it.owner == `this` }
                results += collectAssumedTerms(state)
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
            it.isViolated(state, query)
        }
        log.debug("Acquired $result")
        return state to result
    }
}

typealias CallInstChain = List<CallInst>

data class PredicateStateWithInfo(
        var predicateState: PredicateState,
        val callStack: List<CallInst>,
        val asserts: Set<Term>
)