package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ConstByteTerm(val value: Byte) : Term(value.toString(), TF.getByteType(), arrayOf()) {
    override fun print() = name
}