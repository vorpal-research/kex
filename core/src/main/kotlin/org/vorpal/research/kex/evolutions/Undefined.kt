package org.vorpal.research.kex.evolutions

import ru.spbstu.Apply
import ru.spbstu.Symbolic

object Undefined : Apply("\\undefined") {
    override fun toString(): String = "\\undefined"

    override fun copy(arguments: List<Symbolic>): Symbolic {
        require(arguments.isEmpty())
        return this
    }
}