package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.ArgumentTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.ValueTerm
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction


class Checker(val method: Method, val loader: ClassLoader, private val psa: PredicateStateAnalysis) {
    private val isInliningEnabled = GlobalConfig.getBooleanValue("smt", "ps-inlining", true)
    private val isMemspacingEnabled = GlobalConfig.getBooleanValue("smt", "memspacing", true)
    private val isSlicingEnabled = GlobalConfig.getBooleanValue("smt", "slicing", false)
    private val logQuery = GlobalConfig.getBooleanValue("smt", "logQuery", false)

    private val builder = psa.builder(method)

    fun checkReachable(inst: Instruction): Result {
        log.debug("Checking reachability of ${inst.print()}")

        var state = builder.getInstructionState(inst)
                ?: return Result.UnknownResult("Can't get state for instruction ${inst.print()}, maybe it's unreachable")

        if (logQuery) log.debug("State: $state")

        if (isInliningEnabled) {
            log.debug("Inlining started...")
            state = MethodInliner(method, psa).apply(state)
            log.debug("Inlining finished")
        }

        state = IntrinsicAdapter.apply(state)
        state = TypeInfoAdapter(method, loader).apply(state)
        state = Optimizer.transform(state).simplify()
        state = ConstantPropagator.apply(state).simplify()
        state = BoolTypeAdapter(method.cm.type).apply(state).simplify()

        if (isMemspacingEnabled) {
            log.debug("Memspacing started...")
            state = MemorySpacer(state).apply(state).simplify()
            log.debug("Memspacing finished")
        }

        var query = state.filterByType(PredicateType.Path()).simplify()

        if (isSlicingEnabled) {
            log.debug("Slicing started...")

            val variables = VariableCollector(state)
            val slicingTerms = run {
                val `this` = variables.find { it is ValueTerm && it.name == "this" }

                val results = hashSetOf<Term>()
                if (`this` != null) results += `this`

                results += variables.asSequence().filter { it is ArgumentTerm }
                results += variables.asSequence().filter { it is FieldTerm && it.owner == `this` }
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

        state = Optimizer.apply(state)
        query = Optimizer.apply(query).simplify()
        if (logQuery) {
            log.debug("Simplified state: $state")
            log.debug("Path: $query")
        }

        val result = SMTProxySolver(method.cm.type).isPathPossible(state, query)
        log.debug("Acquired $result")
        return result
    }
}