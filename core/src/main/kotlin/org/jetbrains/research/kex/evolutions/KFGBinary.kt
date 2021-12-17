package org.jetbrains.research.kex.evolutions

import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import ru.spbstu.Apply
import ru.spbstu.Symbolic

class KFGBinary(val opcode: BinaryOpcode, lhv: Symbolic, rhv: Symbolic) :
    Apply("\\operator${opcode.name}", lhv, rhv) {

    override fun copy(arguments: List<Symbolic>): Symbolic {
        require(arguments.size == 2)
        return KFGBinary(opcode, arguments[0], arguments[1])
    }

    override fun toString(): String = "${this.function}(${this.arguments.joinToString(", ")})"
}