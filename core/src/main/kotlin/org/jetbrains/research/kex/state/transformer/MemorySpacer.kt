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
        is ArrayLengthTerm -> tf.getArrayLength(memspaced, getArrayRef())
        is ArrayLoadTerm -> tf.getArrayLoad(memspaced, getArrayRef(), getIndex())
        is BinaryTerm -> tf.getBinary(memspaced, opcode, getLhv(), getRhv())
        is CallTerm -> when {
            isStatic -> tf.getCall(memspaced, method, getArguments())
            else -> tf.getCall(memspaced, method, getObjectRef(), getArguments())
        }
        is CastTerm -> tf.getCast(memspaced, getOperand())
        is CmpTerm -> tf.getCmp(memspaced, opcode, getLhv(), getRhv())
        is ConstStringTerm -> tf.getString(memspaced, name)
        is FieldLoadTerm -> when {
            isStatic -> tf.getFieldLoad(memspaced, classType, getFieldName())
            else -> tf.getFieldLoad(memspaced, getObjectRef(), getFieldName())
        }
        is NegTerm -> tf.getNegTerm(memspaced, getOperand())
        is ReturnValueTerm -> tf.getReturn(memspaced, method)
        is ValueTerm -> tf.getValue(memspaced, getValueName())
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