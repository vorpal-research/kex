package org.jetbrains.research.kex.state.term

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Class

class ConstClassTerm(val `class`: Class) : Term("$`class`.class", TF.getRefType(`class`), arrayOf()) {
    override fun print() = name
}