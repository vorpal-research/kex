package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class ArgumentTerm(override val type: KexType, val index: Int) : Term() {
    override val name = "arg$$index"
    override val subTerms by lazy { listOf<Term>() }

    override fun <T: Transformer<T>> accept(t: Transformer<T>) = this

    override fun hashCode() = 31 * super.hashCode() + index.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ArgumentTerm
        return this.index == other.index && super.equals(other)
    }
}
