package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.InheritorOf
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kfg.ir.Method

@InheritorOf("Term")
class CallTerm(type: KexType, owner: Term, val method: Method, operands: List<Term>)
    : Term("", type, listOf(owner).plus(operands)) {

    val owner: Term
        get() = subterms[0]

    val arguments: List<Term>
        get() = subterms.drop(1)

    val isStatic: Boolean
        get() = owner is ConstClassTerm

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$owner.${method.name}(")
        sb.append(arguments.joinToString())
        sb.append(")")
        return sb.toString()
    }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val towner = t.transform(owner)
        val targuments = arguments.map { t.transform(it) }
        return when {
            towner == owner && targuments.contentEquals(arguments) -> this
            else -> t.tf.getCall(method, towner, targuments)
        }
    }
}