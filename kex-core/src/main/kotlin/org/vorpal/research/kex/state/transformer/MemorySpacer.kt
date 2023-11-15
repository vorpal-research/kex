package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.CastTerm
import org.vorpal.research.kex.state.term.ClassAccessTerm
import org.vorpal.research.kex.state.term.CmpTerm
import org.vorpal.research.kex.state.term.ConcatTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.ConstStringTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.NegTerm
import org.vorpal.research.kex.state.term.NullTerm
import org.vorpal.research.kex.state.term.ReturnValueTerm
import org.vorpal.research.kex.state.term.StaticClassRefTerm
import org.vorpal.research.kex.state.term.SubstringTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.ToStringTerm
import org.vorpal.research.kex.state.term.UndefTerm
import org.vorpal.research.kex.state.term.ValueTerm
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

fun Term.withMemspace(memspace: Int): Term {
    val type = this.type as? KexPointer ?: return this
    val memspaced = type.withMemspace(memspace)
    return term {
        when (this@withMemspace) {
            is ArgumentTerm -> termFactory.getArgument(memspaced, index)
            is ArrayIndexTerm -> termFactory.getArrayIndex(memspaced, arrayRef, index)
            is ArrayLoadTerm -> termFactory.getArrayLoad(memspaced, arrayRef)
            is BinaryTerm -> termFactory.getBinary(memspaced, opcode, lhv, rhv)
            is CallTerm -> termFactory.getCall(memspaced, owner, method, arguments)
            is CastTerm -> termFactory.getCast(memspaced, operand)
            is ClassAccessTerm -> termFactory.getClassAccess(memspaced, operand)
            is ConcatTerm -> termFactory.getConcat(memspaced, lhv, rhv)
            is CmpTerm -> termFactory.getCmp(memspaced, opcode, lhv, rhv)
            is ConstStringTerm -> termFactory.getString(memspaced, value)
            is ConstClassTerm -> termFactory.getClass(memspaced, constantType)
            is StaticClassRefTerm -> termFactory.getStaticRef(memspaced as KexClass)
            is FieldLoadTerm -> termFactory.getFieldLoad(memspaced, field)
            is FieldTerm -> termFactory.getField(memspaced, owner, fieldName)
            is LambdaTerm -> this@withMemspace
            is NegTerm -> termFactory.getNegTerm(memspaced, operand)
            is ReturnValueTerm -> termFactory.getReturn(memspaced, method)
            is SubstringTerm -> termFactory.getSubstring(memspaced, string, offset, length)
            is ToStringTerm -> termFactory.getToString(memspaced, value)
            is ValueTerm -> termFactory.getValue(memspaced, name)
            is UndefTerm -> termFactory.getUndef(memspaced)
            is NullTerm -> this@withMemspace
            else -> this@withMemspace.also { log.warn("Memspacing unexpected term type: ${this@withMemspace}") }
        }
    }
}

fun Term.dropMemspace(): Term = this.withMemspace(KexPointer.defaultMemspace)

val Term.memspace: Int
    get() = (this.type as? KexPointer)?.memspace
            ?: unreachable { log.error("Trying to get memspace of primary type: $type") }

class MemorySpacer(ps: PredicateState) : Transformer<MemorySpacer> {
    private val aa = StensgaardAA().apply { apply(ps) }
    private val indices = hashMapOf<Token?, Int>(null to 0)
    private var index = 1

    private fun getIndex(token: Token?) = indices.getOrPut(token) { index++ }
    private fun getMemspace(term: Term) = getIndex(aa.getDereferenced(term))

    override fun transformTerm(term: Term) = when (term.type) {
        is KexPointer -> term.withMemspace(getMemspace(term))
        else -> term
    }
}
