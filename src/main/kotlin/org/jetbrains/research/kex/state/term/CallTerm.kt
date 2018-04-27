package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

class CallTerm(type: Type, val method: Method, operands: Array<Term>) : Term("", type, operands) {
    fun getObjectRef() = if (method.isStatic()) null else subterms[0]
    fun getArguments() = if (method.isStatic()) subterms else subterms.drop(1).toTypedArray()

    override fun print(): String {
        val sb = StringBuilder()
        if (method.isStatic()) sb.append("${method.`class`}")
        else sb.append("${getObjectRef()}.")
        sb.append("${method.name}(")
        val arguments = getArguments()
        arguments.take(1).forEach { sb.append(it) }
        arguments.drop(1).forEach { sb.append(", $it") }
        sb.append(")")
        return sb.toString()
    }
}