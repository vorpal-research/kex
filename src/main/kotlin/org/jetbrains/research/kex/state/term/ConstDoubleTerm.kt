package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ConstDoubleTerm(val value: Double) : Term(value.toString(), TF.getDoubleType(), arrayOf()) {
    override fun print() = name
}