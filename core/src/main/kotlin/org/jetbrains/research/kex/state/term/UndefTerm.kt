package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer

@InheritorOf("Term")
class UndefTerm(type: KexType) : Term("<undef>", type, arrayListOf()) {
    override fun print() = this.name

    override fun <T : Transformer<T>> accept(t: Transformer<T>) = this
}