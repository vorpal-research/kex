package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ConstIntTerm(val value: Int): Term(value.toString(), TF.getIntType(), arrayOf()) {
    override fun print() = name
}