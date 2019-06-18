package org.jetbrains.research.kex.state.term

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode

@InheritorOf("Term")
@Serializable
class BinaryTerm(
        override val type: KexType,
        @ContextualSerialization val opcode: BinaryOpcode,
        val lhv: Term,
        val rhv: Term) : Term() {
    override val name = "$lhv $opcode $rhv"
    override val subterms: List<Term>
        get() = listOf(lhv, rhv)

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tlhv = t.transform(lhv)
        val trhv = t.transform(rhv)
        return when {
            tlhv == lhv && trhv == rhv -> this
            else -> t.tf.getBinary(type, opcode, tlhv, trhv)
        }
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), opcode)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as BinaryTerm
        return super.equals(other) && this.opcode == other.opcode
    }
}