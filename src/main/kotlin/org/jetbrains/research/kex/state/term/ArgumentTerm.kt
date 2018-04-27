package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.type.Type

class ArgumentTerm(val index: Int, type: Type) : Term("arg$$index", type, arrayOf()) {
    override fun print() = name
}