package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kex.util.defaultHashCode

class InstanceOfTerm(val checkedType: Type, operand: Term) : Term("", TF.getBoolType(), listOf(operand)) {
    val operand get() = subterms[0]
    override fun print() = "$operand instanceof $checkedType"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val toperand = t.transform(operand)
        return if (toperand == operand) this else t.tf.getInstanceOf(checkedType, toperand)
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), checkedType)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass == this.javaClass) return false
        other as InstanceOfTerm
        return super.equals(other) and (this.checkedType == other.checkedType)
    }
}