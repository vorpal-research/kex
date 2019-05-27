package org.jetbrains.research.kex.asm.analysis

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.state.PredicateStateBuilder
import org.jetbrains.research.kex.config.GlobalConfig
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
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.util.*


private val isInliningEnabled = GlobalConfig.getBooleanValue("smt", "ps-inlining", true)
private val isMemspacingEnabled = GlobalConfig.getBooleanValue("smt", "memspacing", true)
private val isSlicingEnabled = GlobalConfig.getBooleanValue("smt", "slicing", false)
private val logQuery = GlobalConfig.getBooleanValue("smt", "logQuery", false)

class ViolationChecker(override val cm: ClassManager,
                       private val psa: PredicateStateAnalysis) : MethodVisitor {
    private val tf = TermFactory
    private val pf = PredicateFactory
    private lateinit var builder: PredicateStateBuilder
    private lateinit var method: Method
    private lateinit var currentBlock: BasicBlock
    private val failingBlocks = mutableSetOf<BasicBlock>()
    private val visitedBlocks = mutableSetOf<BasicBlock>()

    override fun cleanup() {
        failingBlocks.clear()
        visitedBlocks.clear()
    }

    override fun visit(method: Method) {
        cleanup()

        this.builder = psa.builder(method)
        this.method = method

        val query = ArrayDeque<BasicBlock>()
        if (method.isNotEmpty())
            query.push(method.entry)

        while (query.isNotEmpty()) {
            currentBlock = query.pollFirst()

            super.visitBasicBlock(currentBlock)

            if (currentBlock !in failingBlocks)
                query += currentBlock.successors.filter { it !in visitedBlocks }.filter { it !in query }

            visitedBlocks += currentBlock
        }
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val arrayRef = tf.getValue(inst.arrayRef)
        val length = tf.getArrayLength(arrayRef)
        val index = tf.getValue(inst.index)
        val state = builder.getInstructionState(inst) ?: return

        visitArrayAccess(state, arrayRef, length, index)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val arrayRef = tf.getValue(inst.arrayRef)
        val length = tf.getArrayLength(arrayRef)
        val index = tf.getValue(inst.index)
        val state = builder.getInstructionState(inst) ?: return

        visitArrayAccess(state, arrayRef, length, index)
    }

    private fun visitArrayAccess(state: PredicateState, arrayRef: Term, length: Term, index: Term) {
        val refQuery = pf.getEquality(
                tf.getCmp(CmpOpcode.Neq(), arrayRef, tf.getNull()),
                tf.getTrue(),
                PredicateType.Require()
        ).wrap()
        if (check(state, refQuery) == Result.UnsatResult) {
            failingBlocks += currentBlock
            return
        }
        val nonNullState = state + refQuery

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

        if (check(nonNullState, indexQuery) == Result.UnsatResult) {
            failingBlocks += currentBlock
            return
        }
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        if (!inst.hasOwner) return
        val state = builder.getInstructionState(inst) ?: return

        val `object` = tf.getValue(inst.owner)
        visitObjectAccess(state, `object`)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        if (!inst.hasOwner) return
        val state = builder.getInstructionState(inst) ?: return

        val `object` = tf.getValue(inst.owner)
        visitObjectAccess(state, `object`)
    }

    private fun visitObjectAccess(state: PredicateState, `object`: Term) {
        val refQuery = pf.getEquality(
                tf.getCmp(CmpOpcode.Neq(), `object`, tf.getNull()),
                tf.getTrue(),
                PredicateType.Require()
        ).wrap()
        if (check(state, refQuery) == Result.UnsatResult) {
            failingBlocks += currentBlock
            return
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
            log.debug("Path: $query")
        }

        val solver = SMTProxySolver(method.cm.type)
        val result = solver.isPathPossible(state, query)
        solver.cleanup()
        log.debug("Acquired $result")
        return result
    }
}