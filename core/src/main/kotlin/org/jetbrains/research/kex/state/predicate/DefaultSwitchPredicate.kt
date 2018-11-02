package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

@InheritorOf("Predicate")
class DefaultSwitchPredicate(cond: Term, cases: List<Term>, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, listOf(cond).plus(cases)) {
    val cond: Term
        get() = operands[0]

    val cases: List<Term>
        get() = operands.drop(1)

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$cond !in (")
        val cases = cases
        cases.take(1).forEach { sb.append(it) }
        cases.drop(1).forEach { sb.append(", $it") }
        sb.append(")")
        return sb.toString()
    }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tcond = t.transform(cond)
        val tcases = cases.map { t.transform(it) }
        return when {
            tcond == cond && tcases == cases -> this
            else -> t.pf.getDefaultSwitchPredicate(tcond, tcases, type)
        }
    }
}