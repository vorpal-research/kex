package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.type.ArrayType

class ArrayStorePredicate(arrayRef: Term, value: Term, type: PredicateType = PredicateType.State(), location: Location = Location())
    : Predicate(type, location, listOf(arrayRef, value)) {
    fun getArrayRef() = operands[0]
    fun getValue() = operands[1]
    fun getComponentType() = (getArrayRef().type as? ArrayType)?.component ?: unreachable { log.error("Non-array type of array ref") }

    override fun print() = "*(${getArrayRef()}) = ${getValue()}"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val ref = t.transform(getArrayRef())
        val store = t.transform(getValue())
        return when {
            ref == getArrayRef() && store == getValue() -> this
            else -> t.pf.getArrayStore(ref, store, type)
        }
    }
}