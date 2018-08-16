package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kfg.type.Reference

class CFGTracker : Transformer<CFGTracker> {
    var currentDominators = setOf<Predicate>()
    val dominatorMap = hashMapOf<Predicate, Set<Predicate>>()

    override fun transform(predicate: Predicate): Predicate {
        if (predicate.type != PredicateType.State()) {
            currentDominators += predicate
        }
        dominatorMap[predicate] = dominatorMap.getOrElse(predicate, ::setOf) + currentDominators
        return predicate
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val entryDominators = currentDominators
        var totalDomsinators = currentDominators

        for (branch in ps.choices) {
            currentDominators = entryDominators
            super.transform(branch)
            totalDomsinators += currentDominators
        }

        currentDominators = entryDominators
        return ps
    }

    fun getDominatingPaths(predicate: Predicate) = dominatorMap.getOrElse(predicate, ::setOf)
    fun getFinalPath() = currentDominators

    fun reset() {
        currentDominators = setOf()
        dominatorMap.clear()
    }
}

private fun Predicate.inverse(): Predicate = when {
    this is EqualityPredicate -> when (rhv) {
        TermFactory.getTrue() -> PredicateFactory.getEquality(lhv, TermFactory.getFalse(), type, location)
        TermFactory.getFalse() -> PredicateFactory.getEquality(lhv, TermFactory.getTrue(), type, location)
        else -> this
    }
    else -> this
}

class Slicer(val state: PredicateState, sliceTerms: Set<Term>, val aa: AliasAnalysis) : DeletingTransformer<Slicer> {
    override val removablePredicates = hashSetOf<Predicate>()

    val sliceVars = hashSetOf<Term>()
    val slicePtrs = hashSetOf<Term>()
    val cfg = CFGTracker()
    var currentPath = setOf<Predicate>()

    val isInterestingTerm = { term: Term -> Term.isNamed(term) }

    init {
        sliceTerms
                .filter(isInterestingTerm)
                .forEach { addSliceTerm(it) }
    }

    constructor(state: PredicateState, query: PredicateState, aa: AliasAnalysis)
            : this(state, TermCollector.getFullTermSet(query), aa)

    constructor(state: PredicateState, query: PredicateState, sliceTerms: Set<Term>, aa: AliasAnalysis)
            : this(state, TermCollector.getFullTermSet(query) + sliceTerms, aa)


    private fun addSliceTerm(term: Term) = when {
        term.type is Reference -> slicePtrs.add(term)
        else -> sliceVars.add(term)
    }

    private fun addCFGDeps(predicate: Predicate) {
        currentPath = cfg.getDominatingPaths(predicate)
    }

    private fun checkVars(lhv: Set<Term>, rhv: Set<Term>) = when {
        lhv.filterNot { it.type is Reference }.any { sliceVars.contains(it) } -> {
            rhv.forEach { addSliceTerm(it) }
            true
        }
        else -> false
    }

    private fun checkPtrs(predicate: Predicate, lhv: Set<Term>, rhv: Set<Term>): Boolean {
        if (lhv.isEmpty()) return false

        if (lhv.filter { it.type is Reference }.any { slicePtrs.contains(it) }) {
            rhv.forEach { addSliceTerm(it) }
            return true
        }

        if (predicate is ArrayStorePredicate
                || predicate is FieldStorePredicate
                || predicate is NewPredicate
                || predicate is NewArrayPredicate) {
            if (lhv.filter { it.type is Reference }.any { ref ->
                        slicePtrs.any { slice -> aa.mayAlias(ref, slice) }
                    }) {
                lhv.forEach { addSliceTerm(it) }
                rhv.forEach { addSliceTerm(it) }
                return true
            }
        }
        return false
    }

    override fun transform(ps: PredicateState): PredicateState {
        cfg.reset()

        cfg.transform(ps)
        currentPath = cfg.getFinalPath()

        val reversed = ps.reverse()
        return super.transform(reversed).reverse().simplify()
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val psi = ps.simplify()
        return when (psi) {
            is ChoiceState -> {
                val savedDeps = currentPath

                val result = psi.fmap {
                    currentPath = savedDeps
                    super.transform(it)
                }
                currentPath = savedDeps
                return result
            }
            else -> super.transformChoice(ps)
        }
    }

    override fun transformBase(predicate: Predicate): Predicate {
        if (predicate.type != PredicateType.State()) {
            val inversed = predicate.inverse()
            if ((predicate in currentPath) and (inversed !in currentPath)) {
                for (op in predicate.operands) {
                    TermCollector
                            .getFullTermSet(op)
                            .filter(isInterestingTerm)
                            .forEach { addSliceTerm(it) }
                }
                addCFGDeps(predicate)
            } else {
                removablePredicates.add(predicate)
            }
            return predicate
        }

        val reciever = Predicate.getReciever(predicate)
        val lhvTerms = hashSetOf<Term>()
        if (reciever != null) {
            lhvTerms.addAll(TermCollector.getFullTermSet(reciever).filter(isInterestingTerm))
        }

        val rhvTerms = hashSetOf<Term>()
        for (rhv in predicate.operands.drop(1)) {
            rhvTerms.addAll(TermCollector.getFullTermSet(rhv).filter(isInterestingTerm))
        }

        val asVar = checkVars(lhvTerms, rhvTerms)
        val asPtr = checkPtrs(predicate, lhvTerms, rhvTerms)
        if (!(asVar || asPtr)) removablePredicates.add(predicate)
        return predicate
    }
}