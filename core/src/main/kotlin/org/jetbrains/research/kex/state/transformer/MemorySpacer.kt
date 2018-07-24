package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Reference
import org.jetbrains.research.kfg.type.Type

interface Memspaced<out T : Reference> {
    val memspace: Int
}

class MemspacedClassType(override val memspace: Int, `class`: Class) : ClassType(`class`), Memspaced<ClassType> {
    override fun hashCode() = super.hashCode()
    override fun equals(other: Any?) = when {
        this === other -> true
        other is ClassType -> this.`class` == other.`class`
        else -> false
    }
}
class MemspacedArrayType(override val memspace: Int, component: Type) : ArrayType(component), Memspaced<ArrayType> {
    override fun hashCode() = super.hashCode()
    override fun equals(other: Any?) = when {
        this === other -> true
        other is ArrayType -> this.component == other.component
        else -> false
    }
}

fun Type.memspaced(memspace: Int) = when (this) {
    is ClassType -> MemspacedClassType(memspace, `class`)
    is ArrayType -> MemspacedArrayType(memspace, component)
    else -> this
}

fun Term.withMemspace(memspace: Int): Term {
    val memspaced = type.memspaced(memspace)
    val tf = TermFactory
    return when (this) {
        is ArgumentTerm -> tf.getArgument(memspaced, index)
        is ArrayIndexTerm -> tf.getArrayIndex(memspaced, arrayRef, index)
        is ArrayLengthTerm -> tf.getArrayLength(memspaced, arrayRef)
        is ArrayLoadTerm -> tf.getArrayLoad(memspaced, arrayRef)
        is BinaryTerm -> tf.getBinary(memspaced, opcode, lhv, rhv)
        is CallTerm -> tf.getCall(memspaced, owner, method, arguments)
        is CastTerm -> tf.getCast(memspaced, operand)
        is CmpTerm -> tf.getCmp(memspaced, opcode, lhv, rhv)
        is ConstStringTerm -> tf.getString(memspaced, name)
        is ConstClassTerm -> tf.getClass(memspaced, `class`)
        is FieldLoadTerm -> tf.getFieldLoad(memspaced, field)
        is FieldTerm -> tf.getField(memspaced, owner, fieldName)
        is NegTerm -> tf.getNegTerm(memspaced, operand)
        is ReturnValueTerm -> tf.getReturn(memspaced, method)
        is ValueTerm -> tf.getValue(memspaced, valueName)
        else -> {
            log.warn("Memspacing unexpected term type: $this")
            this
        }
    }
}

class MemorySpacer(ps: PredicateState) : Transformer<MemorySpacer> {
    val aa = AliasAnalyzer()
    val indices = mutableMapOf<Token, Int>()
    var index = 0

    init {
        aa.transform(ps)
    }

    private fun getIndex(token: Token) = indices.getOrPut(token) { index++ }
    private fun getMemspace(term: Term) = getIndex(aa.getDereferenced(term))

    override fun transformTerm(term: Term) = when {
        term.type is Reference -> term.withMemspace(getMemspace(term))
        else -> super.transformTerm(term)
    }
}