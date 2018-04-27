package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ArrayLengthTerm(arrayRef: Term) : Term("", TF.getIntType(), arrayOf(arrayRef)) {
    fun getArrayRef() = subterms[0]

    override fun print() = "${getArrayRef()}.length"
}