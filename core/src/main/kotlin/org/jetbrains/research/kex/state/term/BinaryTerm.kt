package org.jetbrains.research.kex.state.term

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kthelper.defaultHashCode

@InheritorOf("Term")
@Serializable
class BinaryTerm(
        override val type: KexType,
        @Contextual val opcode: BinaryOpcode,
        val lhv: Term,
        val rhv: Term) : Term() {
    override val name = "$lhv $opcode $rhv"
    override val subTerms by lazy { listOf(lhv, rhv) }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val tLhv = t.transform(lhv)
        val tRhv = t.transform(rhv)
        return when {
            tLhv == lhv && tRhv == rhv -> this
            else -> term { tf.getBinary(type, opcode, tLhv, tRhv) }
        }
    }

    override fun hashCode() = defaultHashCode(super.hashCode(), opcode)
    override fun equals(other: Any?): Boolean {
        if (other?.javaClass != this.javaClass) return false
        other as BinaryTerm
        return super.equals(other) && this.opcode == other.opcode
    }
}