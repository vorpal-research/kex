package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term

class DefaultSwitchPredicate(cond: Term, cases: Array<Term>, type: PredicateType = PredicateType.State())
    : Predicate(type, arrayOf(cond).plus(cases)) {
    fun getCond() = operands[0]
    fun getCases() = operands.drop(1).toTypedArray()

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("${getCond()} !in (")
        val cases = getCases()
        cases.take(1).forEach { sb.append(it) }
        cases.drop(1).forEach { sb.append(", $it") }
        sb.append(")")
        return sb.toString()
    }
}