package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.state.term.isNamed

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

private fun Predicate.inverse(): Predicate = when (this) {
    is EqualityPredicate -> when (rhv) {
        TermFactory.getTrue() -> PredicateFactory.getEquality(lhv, TermFactory.getFalse(), type, location)
        TermFactory.getFalse() -> PredicateFactory.getEquality(lhv, TermFactory.getTrue(), type, location)
        else -> this
    }
    else -> this
}

class Slicer(val state: PredicateState, sliceTerms: Set<Term>, val aa: AliasAnalysis) : Transformer<Slicer> {
    private val sliceVars = hashSetOf<Term>()
    private val slicePtrs = hashSetOf<Term>()
    private val cfg = CFGTracker()
    var currentPath = setOf<Predicate>()

    private val isInterestingTerm = { term: Term -> term.isNamed }

    init {
        sliceTerms.filter(isInterestingTerm).forEach {
            addSliceTerm(it)
        }
    }

    constructor(state: PredicateState, query: PredicateState, aa: AliasAnalysis)
            : this(state, TermCollector.getFullTermSet(query), aa)

    constructor(state: PredicateState, query: PredicateState, sliceTerms: Set<Term>, aa: AliasAnalysis)
            : this(state, TermCollector.getFullTermSet(query) + sliceTerms, aa)


    private fun addSliceTerm(term: Term) = when {
        term.type is KexPointer -> slicePtrs.add(term)
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

        if (lhv.filter { it.type is KexPointer }.any { slicePtrs.contains(it) }) {
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

    override fun transform(ps: PredicateState): PredicateState {
        cfg.apply(ps)
        currentPath = cfg.finalPath

        val reversed = ps.reverse()
        return super.transform(reversed).reverse().simplify()
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
        if (predicate.type == PredicateType.Path()) {
            val inversed = predicate.inverse()
            return when {
                predicate in currentPath && inversed !in currentPath -> {
                    for (op in predicate.operands) {
                        TermCollector
                                .getFullTermSet(op)
                                .filter(isInterestingTerm)
                                .forEach { addSliceTerm(it) }
                    }
                    addCFGDeps(predicate)
                    predicate
                }
                else -> Transformer.Stub
            }
        }

        val receiver = predicate.receiver
        val lhvTerms = when {
            receiver != null -> TermCollector.getFullTermSet(receiver).filter(isInterestingTerm).toSet()
            else -> setOf()
        }
        val rhvTerms = predicate.operands.drop(1).flatMap { TermCollector.getFullTermSet(it) }.toSet()

        val asVar = checkVars(lhvTerms, rhvTerms)
        val asPtr = checkPtrs(predicate, lhvTerms, rhvTerms)
        return when {
            asVar || asPtr -> predicate.also {addCFGDeps(predicate) }
            else -> Transformer.Stub
        }
    }
}