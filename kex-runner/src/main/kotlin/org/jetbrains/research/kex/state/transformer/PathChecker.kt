package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.ConstLongTerm
import org.jetbrains.research.kex.state.term.Term

class PathChecker(val model: SMTModel) : Transformer<PathChecker> {
    var satisfied = true
        private set

    override fun transformBasic(ps: BasicState): PredicateState {
        satisfied = ps.fold(satisfied) { acc, predicate -> acc && checkPredicate(predicate) }
        return ps
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val currentSatisfied = satisfied
        val choiceSatisfactions = mutableListOf<Boolean>()
        for (choice in ps) {
            super.transformBase(choice)
            choiceSatisfactions += satisfied
            satisfied = currentSatisfied
        }
        satisfied = currentSatisfied && choiceSatisfactions.reduce { acc, b -> acc || b }
        return ps
    }

    private fun checkTerms(lhv: Term, rhv: Term, cmp: (Any, Any) -> Boolean): Boolean {
        val lhvValue = when (lhv) {
            is ConstBoolTerm -> lhv.value
            is ConstIntTerm -> lhv.value
            is ConstLongTerm -> lhv.value
            else -> when (val value = model.assignments[lhv]) {
                is ConstBoolTerm -> value.value
                is ConstIntTerm -> value.value
                is ConstLongTerm -> value.value
                else -> unreachable { log.error("Unexpected constant in path $value") }
            }
        }
        val rhvValue = when (rhv) {
            is ConstBoolTerm -> rhv.value
            is ConstIntTerm -> rhv.value
            is ConstLongTerm -> rhv.value
            else -> unreachable { log.error("Unexpected constant in path $rhv") }
        }
        return cmp(lhvValue, rhvValue)
    }

    private fun checkPredicate(path: Predicate): Boolean = when (path) {
        is EqualityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a == b }
        is InequalityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a != b }
        is DefaultSwitchPredicate -> {
            val lhv = path.cond
            val conditions = path.cases
            val lhvValue = when (val value = model.assignments[lhv]) {
                is ConstIntTerm -> value.value
                else -> unreachable { log.error("Unexpected constant in path $value") }
            }
            val condValues = conditions.map { (it as ConstIntTerm).value }
            lhvValue !in condValues
        }
        else -> unreachable { log.error("Unexpected predicate in path: $path") }
    }
}

fun checkPath(model: SMTModel, path: PredicateState): Boolean {
    val pc = PathChecker(model)
    pc.apply(path)
    return pc.satisfied
}