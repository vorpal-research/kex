package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.util.contentEquals
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

class CallTerm(type: Type, owner: Term, val method: Method, operands: List<Term>)
    : Term("", type, listOf(owner).plus(operands)) {

    val owner get() = subterms[0]
    val arguments get() = subterms.drop(1)

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("${owner}.${method.name}(")
        val arguments = arguments
        arguments.take(1).forEach { sb.append(it) }
        arguments.drop(1).forEach { sb.append(", $it") }
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