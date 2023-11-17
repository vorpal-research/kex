package org.vorpal.research.kex.state.predicate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kthelper.assert.asserted

@InheritorOf("Predicate")
@Serializable
class CallPredicate(
        val lhvUnsafe: Term?,
        val callTerm: Term,
        @Required override val type: PredicateType = PredicateType.State(),
        @Required @Contextual override val location: Location = Location()) : Predicate() {
    val hasLhv by lazy { lhvUnsafe != null }
    override val operands by lazy { listOfNotNull(lhvUnsafe, callTerm) }

    constructor(callTerm: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
            : this(null, callTerm, type, location)

    val lhv: Term
        get() = asserted(hasLhv) { operands[0] }

    val call: Term
        get() = if (hasLhv) operands[1] else operands[0]

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tLhv = if (hasLhv) t.transform(lhv) else null
        val tCall = t.transform(call)
        return when {
            hasLhv -> when {
                tLhv == lhv && tCall == call -> this
                else -> predicate(type, location) { tLhv!!.call(tCall) }
            }
            else -> when (tCall) {
                call -> this
                else -> predicate(type, location) { call(tCall) }
            }
        }
    }

    override fun print() = buildString {
        if (hasLhv) append("$lhv = ")
        append(call)
    }
}
