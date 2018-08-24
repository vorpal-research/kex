package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

private val isMemspacingEnabled = GlobalConfig.getBooleanValue("smt", "memspacing", true)
private val isSlicingEnabled = GlobalConfig.getBooleanValue("smt", "slicing", false)

private val logQuery = GlobalConfig.getBooleanValue("smt", "logQuery", false)

class Checker(val method: Method, val psa: PredicateStateAnalysis) {

    fun checkReachable(inst: Instruction): Result {
        log.debug("Checking reachability of ${inst.print()}")

        var state = psa.getInstructionState(inst)
                ?: return Result.UnknownResult("Can't get state for instruction ${inst.print()}, maybe it's unreachable")

        if (logQuery) log.debug("State: $state")

        state = TypeInfoAdapter(method).doit(state)
        state = Optimizer.transform(state).simplify()
        state = ConstantPropagator.transform(state).simplify()
        state = BoolTypeAdapter.transform(state).simplify()

        if (isMemspacingEnabled) {
            log.debug("Memspacing started...")
            state = MemorySpacer(state).transform(state).simplify()
            log.debug("Memspacing finished")
        }

        val aa = StensgaardAA()
        aa.transform(state)

        val query = state.filterByType(PredicateType.Path()).simplify()

        if (isSlicingEnabled) {
            log.debug("Slicing started...")

            val tf = TermFactory
            val slicingTerms = hashSetOf<Term>()
            slicingTerms.addAll(method.desc.args.withIndex().map { (index, type) -> tf.getArgument(type.kexType, index) })

            if (!method.isAbstract) {
                val `this` = tf.getThis(method.`class`)
                slicingTerms.add(`this`)
                for ((_, field) in method.`class`.fields) {
                    slicingTerms.add(tf.getField(field.type.kexType, `this`, tf.getString(field.name)))
                }
            }

            state = Slicer(state, query, slicingTerms, aa).transform(state)
            log.debug("Slicing finished")
        }

        if (logQuery) {
            log.debug("Simplified state: $state")
            log.debug("Path: $query")
        }

        val result = SMTProxySolver().isPathPossible(state, query)
        log.debug("Acquired $result")
        when (result) {
            is Result.SatResult -> log.debug("Reachable")
            is Result.UnsatResult -> log.debug("Unreachable")
            is Result.UnknownResult -> log.debug("Unknown")
        }
        return result
    }
}