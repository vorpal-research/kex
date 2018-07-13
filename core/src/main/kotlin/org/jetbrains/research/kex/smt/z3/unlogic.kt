package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import com.microsoft.z3.enumerations.Z3_lbool
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable

object Z3Unlogic : Loggable {
    val tf = TermFactory

    fun undo(expr: Expr): Term = when (expr) {
        is BoolExpr -> undoBool(expr)
        is BitVecNum -> undoBV(expr)
        is FPNum -> undoFloat(expr)
        else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
    }

    fun undoBool(expr: BoolExpr) = when (expr.boolValue) {
        Z3_lbool.Z3_L_TRUE -> tf.getTrue()
        Z3_lbool.Z3_L_FALSE -> tf.getFalse()
        else -> unreachable { log.error("Trying to undo unknown") }
    }

    fun undoBV(expr: BitVecNum) = when (expr.sortSize) {
        SMTEngine.WORD -> tf.getInt(expr.long.toInt())
        SMTEngine.DWORD -> tf.getLong(expr.long)
        else -> unreachable { log.error("Trying to undo bv with unexpected size: ${expr.sortSize}") }
    }

    fun undoFloat(expr: FPNum): Term {
        val str = expr.toString()
        return when (expr.eBits + expr.sBits) {
            SMTEngine.WORD ->  when (str) {
                "+zero"-> tf.getFloat(0.0F)
                "-zero"-> tf.getFloat(0.0F)
                else -> tf.getFloat(str.toFloat())
            }
            SMTEngine.DWORD -> when (str) {
                "+zero"-> tf.getDouble(0.0)
                "-zero"-> tf.getDouble(0.0)
                else -> tf.getDouble(str.toDouble())
            }
            else -> unreachable { log.error("Trying to undo bv with unexpected size: ebits = ${expr.eBits}, sbits = ${expr.sBits}") }
        }
    }
}