package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

class CallTerm(type: Type, owner: Term, val method: Method, operands: List<Term>)
    : Term("", type, listOf(owner).plus(operands)) {

    fun getOwner() = subterms[0]
    fun getArguments() = subterms.drop(1)

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("${getOwner()}.${method.name}(")
        val arguments = getArguments()
        arguments.take(1).forEach { sb.append(it) }
        arguments.drop(1).forEach { sb.append(", $it") }
        sb.append(")")
        return sb.toString()
    }

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Term {
        val owner = t.transform(getOwner())
        val arguments = getArguments().map { t.transform(it) }
        return when {
            owner == getOwner() && arguments.contentEquals(getArguments()) -> this
            else -> t.tf.getCall(method, owner, arguments)
        }
    }
}