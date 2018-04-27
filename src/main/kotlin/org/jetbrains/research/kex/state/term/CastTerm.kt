package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.type.Type

class CastTerm(type: Type, operand: Term) : Term("", type, arrayOf(operand)) {
    fun getOperand() = subterms[0]
    override fun print() = "($type) ${getOperand()}"
}