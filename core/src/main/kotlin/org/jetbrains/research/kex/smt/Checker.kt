package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.ArgumentTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.ValueTerm
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

private val isInliningEnabled = kexConfig.getBooleanValue("smt", "ps-inlining", true)
private val isMemspacingEnabled = kexConfig.getBooleanValue("smt", "memspacing", true)
private val isSlicingEnabled = kexConfig.getBooleanValue("smt", "slicing", false)
private val logQuery = kexConfig.getBooleanValue("smt", "logQuery", false)

fun checkState(cm: ClassManager,
               ps: PredicateState,
               psa: PredicateStateAnalysis,
               query: PredicateState = ps.filterByType(PredicateType.Path()),
               method: Method? = null,
               loader: ClassLoader? = null): Result {
    var state = ps
    if (logQuery) log.debug("State: $state")

    if (isInliningEnabled && method != null) {
        log.debug("Inlining started...")
        state = MethodInliner(method, psa).apply(state)
        log.debug("Inlining finished")
    }

    state = IntrinsicAdapter.apply(state)
    if (method != null && loader != null)
        state = TypeInfoAdapter(method, loader).apply(state)
    state = Optimizer().apply(state)
    state = ConstantPropagator.apply(state)
    state = BoolTypeAdapter(cm.type).apply(state)

    if (isMemspacingEnabled) {
        log.debug("Memspacing started...")
        state = MemorySpacer(state).apply(state)
        log.debug("Memspacing finished")
    }

    if (isSlicingEnabled) {
        log.debug("Slicing started...")

        val variables = collectVariables(state)
        val slicingTerms = run {
            val `this` = variables.find { it is ValueTerm && it.name == "this" }

            val results = hashSetOf<Term>()
            if (`this` != null) results += `this`

            results += variables.filter { it is ArgumentTerm }
            results += variables.filter { it is FieldTerm && it.owner == `this` }
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
    val query = Optimizer().apply(query)
    if (logQuery) {
        log.debug("Simplified state: $state")
        log.debug("Path: $query")
    }

    val solver = SMTProxySolver(cm.type)
    val result = solver.isPathPossible(state, query)
    solver.cleanup()
    log.debug("Acquired $result")
    return result
}

class Checker(val method: Method, val loader: ClassLoader, private val psa: PredicateStateAnalysis) {
    private val builder = psa.builder(method)

    fun createState(inst: Instruction) = builder.getInstructionState(inst)

    fun checkReachable(inst: Instruction): Result {
        log.debug("Checking reachability of ${inst.print()}")

        val state = createState(inst)
                ?: return Result.UnknownResult("Can't get state for instruction ${inst.print()}")
        return check(state)
    }

    fun check(ps: PredicateState): Result {
        val query = ps.filterByType(PredicateType.Path())

        return checkState(method.cm, ps, psa, query, method, loader)
    }
}
