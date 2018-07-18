package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kfg.ir.Location

class DefaultSwitchPredicate(cond: Term, cases: List<Term>, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, listOf(cond).plus(cases)) {
    fun getCond() = operands[0]
    fun getCases() = operands.drop(1)

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("${getCond()} !in (")
        val cases = getCases()
        cases.take(1).forEach { sb.append(it) }
        cases.drop(1).forEach { sb.append(", $it") }
        sb.append(")")
        return sb.toString()
    }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val cond = t.transform(getCond())
        val cases = getCases().map { t.transform(it) }
        return when {
            cond == getCond() && cases.contentEquals(getCases()) -> this
            else -> t.pf.getDefaultSwitchPredicate(cond, cases, type)
        }
    }
}