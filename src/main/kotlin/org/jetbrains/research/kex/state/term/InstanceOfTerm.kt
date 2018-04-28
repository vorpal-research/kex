package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.defaultHashCode

class InstanceOfTerm(val checkedType: Type, operand: Term) : Term("", TF.getBoolType(), arrayOf(operand)) {
    fun getOperand() = subterms[0]
    override fun print() = "${getOperand()} instanceof $checkedType"

    override fun hashCode() = defaultHashCode(super.hashCode(), checkedType)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass == this.javaClass) return false
        other as InstanceOfTerm
        return super.equals(other) and (this.checkedType == other.checkedType)
    }
}