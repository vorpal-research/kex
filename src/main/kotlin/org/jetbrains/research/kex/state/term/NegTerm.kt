package org.jetbrains.research.kex.state.term

class NegTerm(operand: Term) : Term("", operand.type, arrayOf(operand)) {
    fun getOperand() = subterms[0]

    override fun print() = "-${getOperand()}"
}