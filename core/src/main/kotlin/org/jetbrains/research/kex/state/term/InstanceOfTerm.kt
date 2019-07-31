package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode

@InheritorOf("Term")
@Serializable
class InstanceOfTerm(val checkedType: KexType, val operand: Term) : Term() {
    override val name = "$operand instanceof $checkedType"
    override val type: KexType = KexBool()
    override val subterms by lazy { listOf(operand) }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term =
            when (val toperand = t.transform(operand)) {
                operand -> this
                else -> term { tf.getInstanceOf(checkedType, toperand) }
             }

    override fun hashCode() = defaultHashCode(super.hashCode(), checkedType)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as InstanceOfTerm
        return super.equals(other) && this.checkedType == other.checkedType
    }
}