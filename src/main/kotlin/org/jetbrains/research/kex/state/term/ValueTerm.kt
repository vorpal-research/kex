package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.type.Type

class ValueTerm(type: Type, valueName: String) : Term(valueName, type, arrayOf()) {
    override fun print() = name
}