package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode

class InstanceOfTerm(val checkedType: KexType, operand: Term) : Term("", KexBool, listOf(operand)) {

    val operand: Term
        get() = subterms[0]

    override fun print() = "$operand instanceof $checkedType"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val toperand = t.transform(operand)
        return if (toperand == operand) this else t.tf.getInstanceOf(checkedType, toperand)
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), checkedType)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as InstanceOfTerm
        return super.equals(other) && this.checkedType == other.checkedType
    }
}