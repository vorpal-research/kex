package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.type.Reference

fun Term.withMemspace(memspace: Int): Term {
    if (this.type !is KexPointer) return this
    val memspaced = type.withMemspace(memspace)
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
        is NullTerm -> this
        else -> {
            log.warn("Memspacing unexpected term type: $this")
            this
        }
    }
}

val Term.memspace: Int
    get() = (this.type as? KexPointer)?.memspace ?: KexPointer.defaultMemspace

class MemorySpacer(ps: PredicateState) : Transformer<MemorySpacer> {
    val aa = StensgaardAA()
    val indices = hashMapOf<Token, Int>(null to 0)
    var index = 1

    init {
        aa.transform(ps)
    }

    private fun getIndex(token: Token) = indices.getOrPut(token) { index++ }
    private fun getMemspace(term: Term) = getIndex(aa.getDereferenced(term))

    override fun transformTerm(term: Term) = when {
        term.type is Reference -> term.withMemspace(getMemspace(term))
        else -> term
    }
}