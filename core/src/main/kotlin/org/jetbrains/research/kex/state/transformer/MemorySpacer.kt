package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.*

fun Term.withMemspace(memspace: Int): Term {
    val type = this.type as? KexPointer ?: return this
    val memspaced = type.withMemspace(memspace)
    return term {
        when (this@withMemspace) {
            is ArgumentTerm -> tf.getArgument(memspaced, index)
            is ArrayIndexTerm -> tf.getArrayIndex(memspaced, arrayRef, index)
            is ArrayLengthTerm -> tf.getArrayLength(memspaced, arrayRef)
            is ArrayLoadTerm -> tf.getArrayLoad(memspaced, arrayRef)
            is BinaryTerm -> tf.getBinary(memspaced, opcode, lhv, rhv)
            is CallTerm -> tf.getCall(memspaced, owner, method, arguments)
            is CastTerm -> tf.getCast(memspaced, operand)
            is CmpTerm -> tf.getCmp(memspaced, opcode, lhv, rhv)
            is ConstStringTerm -> tf.getString(memspaced, value)
            is ConstClassTerm -> tf.getClass(memspaced)
            is FieldLoadTerm -> tf.getFieldLoad(memspaced, field)
            is FieldTerm -> tf.getField(memspaced, owner, fieldName)
            is NegTerm -> tf.getNegTerm(memspaced, operand)
            is ReturnValueTerm -> tf.getReturn(memspaced, method)
            is ValueTerm -> tf.getValue(memspaced, valueName)
            is UndefTerm -> tf.getUndef(memspaced)
            is NullTerm -> this@withMemspace
            else -> this@withMemspace.also { log.warn("Memspacing unexpected term type: $this") }
        }
    }
}

fun Term.dropMemspace(): Term = this.withMemspace(KexPointer.defaultMemspace)

val Term.memspace: Int
    get() = (this.type as? KexPointer)?.memspace
            ?: unreachable { log.error("Trying to get memspace of primary type: $type") }

class MemorySpacer(ps: PredicateState) : Transformer<MemorySpacer> {
    private val aa = StensgaardAA().apply { apply(ps) }
    private val indices = hashMapOf<Token, Int>(null to 0)
    private var index = 1

    private fun getIndex(token: Token) = indices.getOrPut(token) { index++ }
    private fun getMemspace(term: Term) = getIndex(aa.getDereferenced(term))

    override fun transformTerm(term: Term) = when (term.type) {
        is KexPointer -> term.withMemspace(getMemspace(term))
        else -> term
    }
}