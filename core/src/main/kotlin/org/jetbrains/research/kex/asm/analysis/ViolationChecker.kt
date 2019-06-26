package org.jetbrains.research.kex.asm.analysis

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.state.PredicateStateBuilder
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.SMTProxySolver
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.state.wrap
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.util.TopologicalSorter
import org.jetbrains.research.kfg.visitor.MethodVisitor

private val isInliningEnabled = kexConfig.getBooleanValue("smt", "ps-inlining", true)
private val isMemspacingEnabled = kexConfig.getBooleanValue("smt", "memspacing", true)
private val isSlicingEnabled = kexConfig.getBooleanValue("smt", "slicing", false)
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)

class ViolationChecker(override val cm: ClassManager,
                       private val psa: PredicateStateAnalysis) : MethodVisitor {
    private val tf = TermFactory
    private val pf = PredicateFactory
    private lateinit var builder: PredicateStateBuilder
    private lateinit var method: Method
    private lateinit var currentBlock: BasicBlock
    private val failingBlocks = mutableSetOf<BasicBlock>()
    private var nonNullityInfo = mutableMapOf<BasicBlock, Set<Value>>()
    private var nonNulls = mutableSetOf<Value>()

    override fun cleanup() {
        failingBlocks.clear()
    }

    override fun visit(method: Method) {
        cleanup()

        this.builder = psa.builder(method)
        this.method = method
        if (method.isEmpty()) return

        val (order, cycled) = TopologicalSorter(method.basicBlocks.toSet()).sort(method.entry)
        require(cycled.isEmpty()) { log.error("No topological sorting for method $method") }

        for (block in order.reversed()) {
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
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val arrayRef = tf.getValue(inst.arrayRef)
        val length = tf.getArrayLength(arrayRef)
        val index = tf.getValue(inst.index)
        val state = builder.getInstructionState(inst) ?: return

        if (inst.arrayRef !in nonNulls && checkNullity(state, arrayRef))
            nonNulls.add(inst.arrayRef)

        checkOutOfBounds(state, length, index)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val arrayRef = tf.getValue(inst.arrayRef)
        val length = tf.getArrayLength(arrayRef)
        val index = tf.getValue(inst.index)
        val state = builder.getInstructionState(inst) ?: return

        if (inst.arrayRef !in nonNulls && checkNullity(state, arrayRef))
            nonNulls.add(inst.arrayRef)

        checkOutOfBounds(state, length, index)
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        if (!inst.hasOwner) return
        if (inst.owner in nonNulls) return
        val state = builder.getInstructionState(inst) ?: return

        val `object` = tf.getValue(inst.owner)
        if (checkNullity(state, `object`)) nonNulls.add(inst.owner)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        if (!inst.hasOwner) return
        if (inst.owner in nonNulls) return
        val state = builder.getInstructionState(inst) ?: return

        val `object` = tf.getValue(inst.owner)
        if (checkNullity(state, `object`)) nonNulls.add(inst.owner)
    }

    override fun visitCallInst(inst: CallInst) {
        if (inst.isStatic) return
        if (inst.callee in nonNulls) return
        val state = builder.getInstructionState(inst) ?:  return

        val `object` = tf.getValue(inst.callee)
        if (checkNullity(state, `object`)) nonNulls.add(inst.callee)
    }

    private fun checkNullity(state: PredicateState, `object`: Term): Boolean {
        val refQuery = pf.getInequality(`object`, tf.getNull(), PredicateType.Require()).wrap()
        return when {
            check(state, refQuery) == Result.UnsatResult -> {
                failingBlocks += currentBlock
                false
            }
            else -> true
        }
    }

    private fun checkOutOfBounds(state: PredicateState, length: Term, index: Term): Boolean {
        var indexQuery = pf.getEquality(
                tf.getCmp(CmpOpcode.Ge(), index, tf.getConstant(0)),
                tf.getTrue(),
                PredicateType.Require()
        ).wrap()
        indexQuery += pf.getEquality(
                tf.getCmp(CmpOpcode.Lt(), index, length),
                tf.getTrue(),
                PredicateType.Require()
        )

        return when {
            check(state, indexQuery) == Result.UnsatResult -> {
                failingBlocks += currentBlock
                false
            }
            else -> true
        }
    }

    private fun check(state_: PredicateState, query_: PredicateState): Result {
        var state = state_
        var query = query_

        if (logQuery) {
            log.run {
                debug(state)
                debug(query)
            }
        }

        if (isInliningEnabled) {
            log.debug("Inlining started...")
            state = MethodInliner(method, psa).apply(state)
            log.debug("Inlining finished")
        }

        state = IntrinsicAdapter.apply(state)
        state = NullityAnnotator(nonNulls.map { tf.getValue(it) }.toSet()).apply(state)
        state = DoubleTypeAdapter().apply(state)
        query = DoubleTypeAdapter().apply(query)
        state = Optimizer().apply(state)
        query = Optimizer().apply(query)
        state = ConstantPropagator.apply(state)
        query = ConstantPropagator.apply(query)
        state = BoolTypeAdapter(method.cm.type).apply(state)

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

        val solver = SMTProxySolver(method.cm.type)
        val result = solver.isPathPossible(state, query)
        solver.cleanup()
        log.debug("Acquired $result")
        return result
    }
}