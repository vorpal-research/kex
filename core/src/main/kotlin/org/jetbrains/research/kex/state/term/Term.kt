package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.BaseType
import org.jetbrains.research.kex.state.InheritanceInfo
import org.jetbrains.research.kex.state.TypeInfo
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.defaultHashCode

@BaseType("Term")
abstract class Term(val name: String, val type: KexType, val subterms: List<Term>) : TypeInfo {
    companion object {

        val terms = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("Term.json")
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo?.inheritors?.map {
                it.name to loader.loadClass(it.inheritorClass)
            }?.toMap() ?: mapOf()
        }

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
        return this.name == other.name && this.type == other.type && this.subterms == other.subterms
    }

    override val inheritors get() = terms
    override val reverseMapping get() = reverse
}

val Term.isNamed
    get() = when (this) {
        is ArgumentTerm -> true
        is ReturnValueTerm -> true
        is ValueTerm -> true
        is ArrayIndexTerm -> true
        is FieldTerm -> true
        else -> false
    }

val Term.isConst
    get() = when (this) {
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