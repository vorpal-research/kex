package org.jetbrains.research.kex.smt.boolector

import org.jetbrains.research.boolector.*
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable

object BoolectorUnlogic {
    val tf = TermFactory

    fun undo(expr: BoolectorNode): Term = when {
        expr.isBoolNode -> undoBool(expr.toBoolNode())
        expr.isBitvecNode -> undoBV(expr.toBitvecNode())
        //is BitvecNode -> undoFloat(expr)//????????????????????????????????????????????????
        else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
    }

    private fun undoBool(expr: BoolNode) = when (expr.assigment()) {
        true -> tf.getTrue()
        false -> tf.getFalse()
        else -> unreachable { log.error("Trying to undo unknown") }
    }

    private fun undoBV(expr: BitvecNode) = when (expr.width) {
        SMTEngine.WORD -> tf.getInt(expr.assignment().toInt())
        SMTEngine.DWORD -> tf.getLong(expr.assignment())
        else -> unreachable { log.error("Trying to undo bv with unexpected size: ${expr.sort}") }
    }

    /*private fun undoFloat(expr: FPNum): Term {
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
                val exponent = Math.pow(2.0, expr.getExponentInt64(false).toDouble())
                val res = sign * significand * exponent
                return termifier(res)
            }
        }*/
}

