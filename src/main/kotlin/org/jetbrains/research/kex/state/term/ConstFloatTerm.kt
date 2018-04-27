package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ConstFloatTerm(val value: Float) : Term(value.toString(), TF.getFloatType(), arrayOf()) {
    override fun print() = name
}