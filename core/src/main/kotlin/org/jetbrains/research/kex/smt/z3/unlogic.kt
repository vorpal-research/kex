package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import com.microsoft.z3.enumerations.Z3_lbool
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable
import java.lang.Math.pow

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
        val isDouble = (expr.eBits + expr.sBits) == SMTEngine.DWORD
        val termifier = { a: Double -> when {
            isDouble -> tf.getDouble(a)
            else -> tf.getFloat(a.toFloat())
        }}
        return when {
            expr.isZero -> termifier(0.0)
            expr.isNaN -> termifier(Double.NaN)
            expr.isInf -> termifier(if(expr.isPositive) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY)
            else -> {
                val sign = if (expr.sign) -1.0 else 1.0
                val significand = expr.significand.toDouble()
                val exponent = pow(2.0, expr.getExponentInt64(false).toDouble())
                val res = sign * significand * exponent
                return termifier(res)
            }
        }
    }
}