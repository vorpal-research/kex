package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kthelper.defaultHashCode
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode

@InheritorOf("Term")
@Serializable
class BinaryTerm(
        override val type: KexType,
        @Contextual val opcode: BinaryOpcode,
        val lhv: Term,
        val rhv: Term) : Term() {
    override val name = "$lhv $opcode $rhv"
    override val subterms by lazy { listOf(lhv, rhv) }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tlhv = t.transform(lhv)
        val trhv = t.transform(rhv)
        return when {
            tlhv == lhv && trhv == rhv -> this
            else -> term { tf.getBinary(type, opcode, tlhv, trhv) }
        }
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), opcode)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as BinaryTerm
        return super.equals(other) && this.opcode == other.opcode
    }
}