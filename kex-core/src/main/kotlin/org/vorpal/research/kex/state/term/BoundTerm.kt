package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class BoundTerm(override val type: KexType, val ptr: Term) : Term() {
    override val name = "bound($ptr)"
    override val subTerms by lazy { listOf(ptr) }

    override fun <T : Transformer<T>> accept(t: Transformer<T>): Term =
            when (val nPtr = t.transform(ptr)) {
                ptr -> this
                else -> term { termFactory.getBound(nPtr) }
            }
}
