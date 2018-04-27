package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ConstShortTerm(val value: Short) : Term(value.toString(), TF.getShortType(), arrayOf()) {
    override fun print() = name
}