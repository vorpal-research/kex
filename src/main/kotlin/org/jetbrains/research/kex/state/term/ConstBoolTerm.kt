package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ConstBoolTerm(val value: Boolean) : Term(value.toString(), TF.getBoolType(), arrayOf()) {
    override fun print() = name
}