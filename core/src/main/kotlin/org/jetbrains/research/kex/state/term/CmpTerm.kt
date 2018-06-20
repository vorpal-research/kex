package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.defaultHashCode

class CmpTerm(type: Type, val opcode: CmpOpcode, lhv: Term, rhv: Term) : Term("", type, listOf(lhv, rhv)) {
    fun getLhv() = subterms[0]
    fun getRhv() = subterms[1]
    override fun print() = "${getLhv()} $opcode ${getRhv()}"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val lhv = t.transform(getLhv())
        val rhv = t.transform(getRhv())
        return when {
            lhv == getLhv() && rhv == getRhv() -> this
            else -> t.tf.getCmp(lhv, rhv, opcode)
        }
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), opcode)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as CmpTerm
        return super.equals(other) and (this.opcode == other.opcode)
    }
}