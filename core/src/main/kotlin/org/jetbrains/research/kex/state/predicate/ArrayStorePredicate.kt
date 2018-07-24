package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.type.ArrayType

class ArrayStorePredicate(arrayRef: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, listOf(arrayRef, value)) {
    val arrayRef get() = operands[0]
    val value get() = operands[1]
    val componentType
        get() = (arrayRef.type as? ArrayType)?.component ?: unreachable { log.error("Non-array type of array ref") }

    override fun print() = "*($arrayRef) = $value"

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Predicate {
        val ref = t.transform(arrayRef)
        val store = t.transform(value)
        return when {
            ref == arrayRef && store == value -> this
            else -> t.pf.getArrayStore(ref, store, type)
        }
    }
}