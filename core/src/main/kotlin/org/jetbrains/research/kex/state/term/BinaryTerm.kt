package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode

class BinaryTerm(type: KexType, val opcode: BinaryOpcode, lhv: Term, rhv: Term) : Term("", type, listOf(lhv, rhv)) {

    val lhv: Term
        get() = subterms[0]

    val rhv: Term
        get() = subterms[1]

    override fun print() = "$lhv $opcode $rhv"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tlhv = t.transform(lhv)
        val trhv = t.transform(rhv)
        return when {
            tlhv == lhv && trhv == rhv -> this
            else -> t.tf.getBinary(opcode, tlhv, trhv)
        }
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), opcode)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as BinaryTerm
        return super.equals(other) && this.opcode == other.opcode
    }
}