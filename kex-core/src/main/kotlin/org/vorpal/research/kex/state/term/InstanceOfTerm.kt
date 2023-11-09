package org.vorpal.research.kex.state.term

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.transformer.Transformer

@InheritorOf("Term")
@Serializable
class InstanceOfTerm(val checkedType: KexType, val operand: Term) : Term() {
    override val name = "$operand instanceof $checkedType"
    override val type: KexType = KexBool
    override val subTerms by lazy { listOf(operand) }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term =
            when (val tOperand = t.transform(operand)) {
                operand -> this
                else -> term { termFactory.getInstanceOf(checkedType, tOperand) }
             }

    override fun hashCode() = 31 * super.hashCode() + checkedType.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as InstanceOfTerm
        return super.equals(other) && this.checkedType == other.checkedType
    }
}
