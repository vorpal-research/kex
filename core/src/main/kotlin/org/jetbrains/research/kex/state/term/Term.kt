package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.TypeInfo
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kex.util.defaultHashCode

abstract class Term(val name: String, val type: KexType, val subterms: List<Term>) : TypeInfo {
    companion object {
        val terms = mapOf<String, Class<*>>(
                "Argument" to ArgumentTerm::class.java,
                "ArrayIndex" to ArrayIndexTerm::class.java,
                "ArrayLength" to ArrayLengthTerm::class.java,
                "ArrayLoad" to ArrayLoadTerm::class.java,
                "Binary" to BinaryTerm::class.java,
                "Bound" to BoundTerm::class.java,
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
                "Field" to FieldTerm::class.java,
                "InstanceOf" to InstanceOfTerm::class.java,
                "Neg" to NegTerm::class.java,
                "Null" to NullTerm::class.java,
                "ReturnValue" to ReturnValueTerm::class.java,
                "Value" to ValueTerm::class.java,
                "Undef" to UndefTerm::class.java
        )

        val reverse = terms.map { it.value to it.key }.toMap()
    }

    abstract fun print(): String
    abstract fun <T : Transformer<T>> accept(t: Transformer<T>): Term

    override fun toString() = print()
    override fun hashCode() = defaultHashCode(name, type, subterms)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Term
        return this.name == other.name && this.type == other.type && this.subterms.contentEquals(other.subterms)
    }

    override val subtypes get() = terms
    override val reverseMapping get() = reverse
}

val Term.isNamed get() = when (this) {
    is ArgumentTerm -> true
    is ReturnValueTerm -> true
    is ValueTerm -> true
    is ArrayIndexTerm -> true
    is FieldTerm -> true
    else -> false
}

val Term.isConst get() = when (this) {
    is ConstBoolTerm -> true
    is ConstByteTerm -> true
    is ConstCharTerm -> true
    is ConstClassTerm -> true
    is ConstDoubleTerm -> true
    is ConstFloatTerm -> true
    is ConstIntTerm -> true
    is ConstLongTerm -> true
    is ConstShortTerm -> true
    is ConstStringTerm -> true
    else -> false
}