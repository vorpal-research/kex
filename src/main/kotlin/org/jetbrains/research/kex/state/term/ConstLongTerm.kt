package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF

class ConstLongTerm(val value: Long) : Term(value.toString(), TF.getLongType(), arrayOf()) {
    override fun print() = name
}