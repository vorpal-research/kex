package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.type.Type

class ArrayLoadTerm(type: Type, arrayRef: Term, index: Term) : Term("", type, arrayOf(arrayRef, index)) {
    fun getArrayRef() = subterms[0]
    fun getIndex() = subterms[1]

    override fun print() = "${getArrayRef()}[${getIndex()}]"
}