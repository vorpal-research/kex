package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kex.util.defaultHashCode

class CmpTerm(type: Type, val opcode: CmpOpcode, lhv: Term, rhv: Term) : Term("", type, listOf(lhv, rhv)) {
    val lhv get() = subterms[0]
    val rhv get() = subterms[1]
    override fun print() = "$lhv $opcode $rhv"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tlhv = t.transform(lhv)
        val trhv = t.transform(rhv)
        return when {
            tlhv == lhv && trhv == rhv -> this
            else -> t.tf.getCmp(opcode, tlhv, trhv)
        }
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), opcode)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as CmpTerm
        return super.equals(other) and (this.opcode == other.opcode)
    }
}