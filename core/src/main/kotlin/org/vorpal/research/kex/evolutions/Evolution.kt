package org.vorpal.research.kex.evolutions

import org.vorpal.research.kex.evolutions.LoopTracker.name
import org.vorpal.research.kfg.visitor.Loop
import ru.spbstu.Apply
import ru.spbstu.Symbolic

/**
 * Represents operations, supported by Evolution.
 *
 */
enum class EvoOpcode(@Suppress("MemberVisibilityCanBePrivate") val rep: String) {
    PLUS("+"), TIMES("*");

    override fun toString(): String = rep
}

/**
 * Represents recurrence chain.
 * @param loop loop id.
 * @param opcode operation, mul or sum.
 * @param lhv left operand, represents base of recurrence.
 * @param rhv right operand, represents induction on each step of the loop.
 * @constructor constructs evolution.
 */
class Evolution(val loop: Loop, val opcode: EvoOpcode, val lhv: Symbolic, val rhv: Symbolic) :
    Apply("\\evolution", lhv, rhv) {
    override fun copy(arguments: List<Symbolic>): Symbolic {
        require(arguments.size == 2)
        return Evolution(loop, opcode, arguments[0], arguments[1])
    }

    private fun argsToString(sb: StringBuilder) {
        sb.append(lhv)
        sb.append(", ")
        sb.append(opcode)
        sb.append(", ")
        if (rhv is Evolution && rhv.loop == loop) rhv.argsToString(sb)
        else sb.append(rhv)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("{")
        argsToString(sb)
        sb.append("}[${loop.name}]")
        return "$sb"
    }
}
