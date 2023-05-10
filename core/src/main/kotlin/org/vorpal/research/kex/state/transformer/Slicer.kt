package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.state.ChoiceState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.hasReceiver
import org.vorpal.research.kex.state.predicate.receiver
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.isNamed
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.StateClause
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState

class CFGTracker : Transformer<CFGTracker> {
    private var currentDominators = setOf<Predicate>()
    private val dominatorMap = hashMapOf<Predicate, Set<Predicate>>()

    override fun apply(ps: PredicateState): PredicateState {
        currentDominators = setOf()
        dominatorMap.clear()
        return super.apply(ps)
    }

    override fun transformBase(predicate: Predicate): Predicate {
        if (predicate.type == PredicateType.Path()) {
            currentDominators = currentDominators + predicate
        }
        dominatorMap[predicate] = dominatorMap.getOrElse(predicate, ::setOf) + currentDominators
        return predicate
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val entryDominators = currentDominators
        var totalDominators = currentDominators

        for (branch in ps.choices) {
            currentDominators = entryDominators
            super.transform(branch)
            totalDominators = totalDominators + currentDominators
        }

        currentDominators = entryDominators
        return ps
    }

    fun getDominatingPaths(predicate: Predicate) = dominatorMap.getOrElse(predicate, ::setOf)
    val finalPath get() = currentDominators
}

class Slicer(
    val state: PredicateState,
    sliceTerms: Set<Term>,
    private val aa: AliasAnalysis
) : Transformer<Slicer> {
    private val sliceVars = hashSetOf<Term>()
    private val slicePtrs = hashSetOf<Term>()
    private val cfg = CFGTracker()
    private var currentPath = setOf<Predicate>()

    private val isInterestingTerm = { term: Term -> term.isNamed }

    init {
        for (term in sliceTerms.filter(isInterestingTerm)) {
            addSliceTerm(term)
        }
    }

    constructor(state: PredicateState, query: PredicateState, sliceTerms: Set<Term>, aa: AliasAnalysis)
            : this(state, TermCollector.getFullTermSet(query) + sliceTerms, aa)


    private fun addSliceTerm(term: Term) = when (term.type) {
        is KexPointer -> slicePtrs.add(term)
        else -> sliceVars.add(term)
    }

    private fun addCFGDeps(predicate: Predicate) {
        currentPath = cfg.getDominatingPaths(predicate)
    }

    private fun checkVars(lhv: Set<Term>, rhv: Set<Term>) = when {
        lhv.filterNot { it.type is KexPointer }.any { sliceVars.contains(it) } -> {
            rhv.forEach { addSliceTerm(it) }
            true
        }

        else -> false
    }

    private fun checkPtrs(predicate: Predicate, lhv: Set<Term>, rhv: Set<Term>): Boolean {
        if (lhv.isEmpty()) return false

        if (lhv.filter { it.type is KexPointer }.any { it in slicePtrs }) {
            rhv.forEach { addSliceTerm(it) }
            return true
        }

        if (predicate.hasReceiver) {
            val lhvPtrs = lhv.filter { it.type is KexPointer }
            if (lhvPtrs.any { ref -> slicePtrs.any { slice -> aa.mayAlias(ref, slice) } }) {
                lhv.forEach { addSliceTerm(it) }
                rhv.forEach { addSliceTerm(it) }
                return true
            }
        }
        return false
    }

    override fun apply(ps: PredicateState): PredicateState {
        cfg.apply(ps)
        currentPath = cfg.finalPath

        val reversed = ps.reverse()
        return super.apply(reversed).reverse().simplify()
    }

    override fun transformChoice(ps: ChoiceState) = when (val psi = ps.simplify()) {
        is ChoiceState -> {
            val savedDeps = currentPath

            val result = psi.fmap {
                currentPath = savedDeps
                super.transform(it)
            }
            currentPath = savedDeps
            result
        }

        else -> super.transformChoice(ps)
    }

    override fun transformBase(predicate: Predicate): Predicate {
        if (predicate.type == PredicateType.Axiom()) {
            return predicate
        }
        if (predicate.type == PredicateType.Path()) {
            addCFGDeps(predicate)
            return predicate
//            val inversed = predicate.inverse()
//            return when {
//                predicate in currentPath && inversed !in currentPath -> {
//                    for (op in predicate.operands) {
//                        TermCollector
//                                .getFullTermSet(op)
//                                .filter(isInterestingTerm)
//                                .forEach { addSliceTerm(it) }
//                    }
//                    addCFGDeps(predicate)
//                    predicate
//                }
//                else -> nothing()
//            }
        }

        val receiver = predicate.receiver
        val lhvTerms = when {
            receiver != null -> TermCollector.getFullTermSet(receiver).filterTo(mutableSetOf(), isInterestingTerm)
            else -> setOf()
        }
        val rhvTerms = predicate.operands.drop(1).flatMapTo(mutableSetOf()) { TermCollector.getFullTermSet(it) }

        val asVar = checkVars(lhvTerms, rhvTerms)
        val asPtr = checkPtrs(predicate, lhvTerms, rhvTerms)
        return when {
            asVar || asPtr -> predicate.also { addCFGDeps(predicate) }
            else -> nothing()
        }
    }
}


class SymbolicStateForwardSlicer(
    sliceTerms: Set<Term>,
    private val aa: AliasAnalysis
) {
    private val sliceVars = hashSetOf<Term>()
    private val slicePtrs = hashSetOf<Term>()

    private val isInterestingTerm = { term: Term -> term.isNamed }

    init {
        for (term in sliceTerms.filter(isInterestingTerm)) {
            addSliceTerm(term)
        }
    }

    private fun addSliceTerm(term: Term) = when (term.type) {
        is KexPointer -> slicePtrs.add(term)
        else -> sliceVars.add(term)
    }

    private fun checkVars(lhv: Set<Term>, rhv: Set<Term>) = when {
        rhv.filterNot { it.type is KexPointer }.any { sliceVars.contains(it) } -> {
            lhv.forEach { addSliceTerm(it) }
            true
        }

        else -> false
    }

    private fun checkPtrs(predicate: Predicate, lhv: Set<Term>, rhv: Set<Term>): Boolean {
        if (rhv.isEmpty()) return false

        if (rhv.filter { it.type is KexPointer }.any { it in slicePtrs }) {
            lhv.forEach { addSliceTerm(it) }
            return true
        }

        if (predicate.hasReceiver) {
            val rhvPtrs = rhv.filter { it.type is KexPointer }
            if (rhvPtrs.any { ref -> slicePtrs.any { slice -> aa.mayAlias(ref, slice) } }) {
                lhv.forEach { addSliceTerm(it) }
                return true
            }
        }
        return false
    }

    fun apply(state: SymbolicState): PersistentSymbolicState {
        var result = persistentSymbolicState()
        for (clause in state.clauses) {
            val transformed = transformBase(clause.predicate) ?: continue
            result = result.copy(
                clauses = result.clauses + StateClause(clause.instruction, transformed)
            )
        }
        for (clause in state.path) {
            val transformed = transformBase(clause.predicate) ?: continue
            result = result.copy(
                path = result.path + PathClause(clause.type, clause.instruction, transformed)
            )
        }
        return result
    }

    fun transformBase(predicate: Predicate): Predicate? {
        val (lhvTerms, rhvTerms) = when (predicate.type) {
            is PredicateType.Path -> emptySet<Term>() to TermCollector.getFullTermSet(predicate)
                .filterTo(mutableSetOf(), isInterestingTerm)

            else -> {
                val lhvTerms = when (val receiver = predicate.receiver) {
                    null -> setOf()
                    else -> TermCollector.getFullTermSet(receiver).filterTo(mutableSetOf(), isInterestingTerm)
                }
                val rhvTerms = predicate.operands.drop(1).flatMapTo(mutableSetOf()) { TermCollector.getFullTermSet(it) }
                lhvTerms to rhvTerms
            }
        }

        val asVar = checkVars(lhvTerms, rhvTerms)
        val asPtr = checkPtrs(predicate, lhvTerms, rhvTerms)
        return when {
            asVar || asPtr -> predicate
            else -> null
        }
    }
}
