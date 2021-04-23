package org.jetbrains.research.kex.asm.analysis.defect

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.analysis.SearchStrategy
import org.jetbrains.research.kex.asm.analysis.UnfilteredDfsStrategy
import org.jetbrains.research.kex.asm.manager.MethodManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.state.PredicateStateBuilder
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.reanimator.Reanimator
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.SMTProxySolver
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.require
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.state.wrap
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.StringConstant
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull
import java.nio.file.Path

private val isMemspacingEnabled by lazy { kexConfig.getBooleanValue("smt", "memspacing", true) }
private val isSlicingEnabled by lazy { kexConfig.getBooleanValue("smt", "slicing", false) }
private val logQuery by lazy { kexConfig.getBooleanValue("smt", "logQuery", false) }

class DefectChecker(
    val ctx: ExecutionContext,
    private val psa: PredicateStateAnalysis
) : MethodVisitor {
    override val cm get() = ctx.cm
    val loader get() = ctx.loader
    val dm = DefectManager
    val im = MethodManager.KexIntrinsicManager
    private lateinit var generator: Reanimator
    private var testIndex = 0
    private lateinit var builder: PredicateStateBuilder
    private lateinit var method: Method
    private lateinit var currentBlock: BasicBlock
    private val failingBlocks = mutableSetOf<BasicBlock>()
    private var nonNullityInfo = mutableMapOf<BasicBlock, Set<Value>>()
    private var nonNulls = mutableSetOf<Value>()

    private fun initializeGenerator() {
        generator = Reanimator(ctx, psa, method)
        testIndex = 0
    }

    override fun cleanup() {
        failingBlocks.clear()
    }

    override fun visit(method: Method) {
        cleanup()

        this.builder = psa.builder(method)
        this.method = method
        if (!method.hasBody) return

        log.debug("Checking method $method")
        log.debug(method.print())

        initializeGenerator()

        val order: SearchStrategy = getSearchStrategy(method)

        for (block in order) {
            currentBlock = block

            val predecessorInfo = currentBlock.predecessors
                .map { nonNullityInfo.getOrPut(it, ::setOf) }
            nonNulls = when {
                predecessorInfo.isNotEmpty() -> predecessorInfo.reduce { prev, curr -> prev.intersect(curr) }
                    .toHashSet()
                else -> mutableSetOf()
            }

            super.visitBasicBlock(currentBlock)

            nonNullityInfo[currentBlock] = nonNulls.toSet()
        }

        generator.emit()
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val arrayRef = term { value(inst.arrayRef) }
        val length = term { arrayRef.length() }
        val index = term { value(inst.index) }
        val state = builder.getInstructionState(inst) ?: return

        checkNullity(inst, state, inst.arrayRef)
        checkOutOfBounds(inst, state, length, index)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val arrayRef = term { value(inst.arrayRef) }
        val length = term { arrayRef.length() }
        val index = term { value(inst.index) }
        val state = builder.getInstructionState(inst) ?: return

        checkNullity(inst, state, inst.arrayRef)
        checkOutOfBounds(inst, state, length, index)
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        if (!inst.hasOwner) return
        val state = builder.getInstructionState(inst) ?: return

        checkNullity(inst, state, inst.owner)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        if (!inst.hasOwner) return
        val state = builder.getInstructionState(inst) ?: return

        checkNullity(inst, state, inst.owner)
    }

    private fun getAllAssertions(assertionsArray: Value): Set<Term> = method.flatten()
        .asSequence()
        .mapNotNull { it as? ArrayStoreInst }
        .filter { it.arrayRef == assertionsArray }
        .map { it.value }
        .map { term { value(it) } }
        .toSet()

    override fun visitCallInst(inst: CallInst) {
        val state = builder.getInstructionState(inst) ?: return

        if (!inst.isStatic) checkNullity(inst, state, inst.callee)

        when (inst.method) {
            im.kexAssert(cm) -> checkAssertion(inst, state, getAllAssertions(inst.args[0]))
            im.kexAssertWithId(cm) -> {
                val id = (inst.args[0] as? StringConstant)?.value
                checkAssertion(inst, state, getAllAssertions(inst.args[1]), id)
            }
        }
    }

    private fun checkNullity(inst: Instruction, state: PredicateState, `object`: Value): Boolean {
        if (`object` in nonNulls) return true
        log.debug("Checking for null pointer exception: ${inst.print()}")
        log.debug("State: $state")
        val objectTerm = term { value(`object`) }
        val refQuery = require { objectTerm inequality null }.wrap()

        val (checkerState, result) = check(state, refQuery)
        return when (result) {
            is Result.SatResult -> {
                failingBlocks += currentBlock
                val (path, testName) = getTest("NullPointerException", checkerState, result) ?: null to null
                dm += Defect.npe(inst.location, null, path, testName)
                false
            }
            else -> true
        }.also {
            // in case of any result this object can be considered non null
            // because further instructions can't fail with NPE if this one did not fail
            nonNulls.add(`object`)
        }
    }

    private fun checkOutOfBounds(inst: Instruction, state: PredicateState, length: Term, index: Term): Boolean {
        log.debug("Checking for out of bounds exception: ${inst.print()}")
        log.debug("State: $state")
        var indexQuery = require { (index ge 0) equality true }.wrap()
        indexQuery += require { (index lt length) equality true }

        val (checkerState, result) = check(state, indexQuery)
        return when (result) {
            is Result.SatResult -> {
                failingBlocks += currentBlock
                val (path, testName) = getTest("OutOfBounds", checkerState, result) ?: null to null
                dm += Defect.oob(inst.location, null, path, testName)
                false
            }
            else -> true
        }
    }

    private fun checkAssertion(
        inst: Instruction,
        state: PredicateState,
        assertions: Set<Term>,
        id: String? = null
    ): Boolean {
        log.debug("Checking for assertion failure: ${inst.print()}")
        log.debug("State: $state")
        val assertionQuery = assertions.map {
            when (it.type) {
                is KexBool -> require { it equality true }
                is KexInt -> require { it equality 1 }
                else -> unreachable { log.error("Unknown assertion variable: $it") }
            }
        }.fold(StateBuilder()) { builder, predicate ->
            builder += predicate
            builder
        }.apply()

        val (checkerState, result) = check(state, assertionQuery)
        return when (result) {
            is Result.SatResult -> {
                failingBlocks += currentBlock
                val (path, testName) = getTest("Assertion", checkerState, result) ?: null to null
                dm += Defect.assert(inst.location, id, path, testName)
                false
            }
            else -> true
        }
    }

    private fun getTest(nameBase: String, state: PredicateState, result: Result.SatResult): Pair<Path, String>? = tryOrNull {
        val testName = "test$nameBase${testIndex++}"
        generator.generate(testName, method, state, result.model)
        generator.printer.targetFile.toPath() to testName
    }

    fun prepareState(ps: PredicateState, typeInfoMap: TypeInfoMap) = transform(ps) {
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +RecursiveInliner(psa) { ConcreteImplInliner(method.cm.type, typeInfoMap, psa, inlineIndex = it) }
//        +MethodInliner(psa)
        +StaticFieldInliner(method.cm, psa)
        +RecursiveInliner(psa) { MethodInliner(psa, inlineIndex = it) }
        +IntrinsicAdapter
        +NullityAnnotator(nonNulls.map { term { value(it) } }.toSet())
        +DoubleTypeAdapter()
        +ReflectionInfoAdapter(method, loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ConstStringAdapter()
        +FieldNormalizer(method.cm)
    }

    private fun getSearchStrategy(method: Method): SearchStrategy = UnfilteredDfsStrategy(method)

    private fun check(state_: PredicateState, query_: PredicateState): Pair<PredicateState, Result> {
        val staticTypeInfoMap = collectStaticTypeInfo(types, state_, TypeInfoMap())
        var state = prepareState(state_, staticTypeInfoMap)
        var query = query_

        if (isMemspacingEnabled) {
            log.debug("Memspacing started...")
            val spacer = MemorySpacer((state.builder() + query).apply())
            state = spacer.apply(state)
            query = spacer.apply(query)
            log.debug("Memspacing finished")
        }

        if (isSlicingEnabled) {
            log.debug("Slicing started...")

            val slicingTerms = run {
                val (`this`, arguments) = collectArguments(state)

                val results = hashSetOf<Term>()

                if (`this` != null) results += `this`
                results += arguments.values
                results += collectVariables(state).filter { it is FieldTerm && it.owner == `this` }
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