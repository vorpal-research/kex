package org.jetbrains.research.kex.smt.z3

import com.microsoft.z3.*
import com.microsoft.z3.enumerations.Z3_lbool
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import java.lang.Math.pow

object Z3Unlogic {
    fun undo(expr: Expr): Term = when (expr) {
        is BoolExpr -> undoBool(expr)
        is BitVecNum -> undoBV(expr)
        is BitVecExpr -> undoBVExpr(expr)
        is FPNum -> undoFloat(expr)
        else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
    }

    private fun undoBVExpr(expr: BitVecExpr): Term {
        return when {
            expr.numArgs == 2 && expr.args[0] is FPRMNum -> {
                val arg = undo(expr.args[1])
                when (expr.args[0] as FPRMNum) {
                    // todo: support all modes
                    else -> arg
                }
            }
            expr.isBVExtract -> undo(expr.args[0])
            // todo: support more bv expressions
            else -> TODO()
        }
    }

    private fun undoBool(expr: BoolExpr) = when (expr.boolValue) {
        Z3_lbool.Z3_L_TRUE -> term { const(true) }
        Z3_lbool.Z3_L_FALSE -> term { const(false) }
        else -> unreachable { log.error("Trying to undo unknown") }
    }

    private fun undoBV(expr: BitVecNum) = when (expr.sortSize) {
        SMTEngine.WORD -> term { const(expr.long.toInt()) }
        SMTEngine.DWORD -> {
            val value = try {
                expr.long
            } catch (e: Z3Exception) {
                expr.bigInteger.toLong()
            }
            term { const(value) }
        }
        else -> unreachable { log.error("Trying to undo bv with unexpected size: ${expr.sortSize}") }
    }

    private fun undoFloat(expr: FPNum): Term {
        val isDouble = (expr.eBits + expr.sBits) == SMTEngine.DWORD
        val termifier = { a: Double -> when {
            isDouble -> term { const(a) }
            else -> term { const(a.toFloat()) }
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