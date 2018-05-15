package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.Sealed
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.defaultHashCode

abstract class Term(val name: String, val type: Type, val subterms: Array<Term>) : Sealed {
    companion object {
        val terms = mapOf<String, Class<*>>(
                "Argument" to ArgumentTerm::class.java,
                "ArrayLength" to ArrayLengthTerm::class.java,
                "ArrayLoad" to ArrayLoadTerm::class.java,
                "Binary" to BinaryTerm::class.java,
                "Call" to CallTerm::class.java,
                "Cast" to CastTerm::class.java,
                "Cmp" to CmpTerm::class.java,
                "ConstBool" to ConstBoolTerm::class.java,
                "ConstByte" to ConstByteTerm::class.java,
                "ConstChar" to ConstCharTerm::class.java,
                "ConstClass" to ConstClassTerm::class.java,
                "ConstDouble" to ConstDoubleTerm::class.java,
                "ConstFloat" to ConstFloatTerm::class.java,
                "ConstInt" to ConstIntTerm::class.java,
                "ConstLong" to ConstLongTerm::class.java,
                "ConstShort" to ConstShortTerm::class.java,
                "ConstString" to ConstStringTerm::class.java,
                "FieldLoad" to FieldLoadTerm::class.java,
                "InstanceOf" to InstanceOfTerm::class.java,
                "Neg" to NegTerm::class.java,
                "Null" to NullTerm::class.java,
                "ReturnValue" to ReturnValueTerm::class.java,
                "Value" to ValueTerm::class.java
        )

        val reverse = terms.map { it.value to it.key }.toMap()
    }

    override fun getSubtypes() = terms
    override fun getReverseMapping() = reverse

    override fun hashCode() = defaultHashCode(name, type, *subterms)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Term
        return this.name == other.name && this.type == other.type && this.subterms.contentEquals(other.subterms)
    }

    override fun toString() = print()

    abstract fun print(): String
    abstract fun <T: Transformer<T>> accept(t: Transformer<T>): Term
}