package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class ValueTerm(type: KexType, valueName: Term) : Term(valueName.name, type, listOf(valueName)) {

    val valueName: Term
        get() = subterms[0]

    override fun print() = name
    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this
}