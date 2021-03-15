package org.jetbrains.research.kex.smt.z3

import org.jetbrains.research.kthelper.*
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import com.microsoft.z3.*
import com.microsoft.z3.enumerations.Z3_lbool
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.state.term.*
import java.lang.Math.pow
import kotlin.math.pow

object Z3Unlogic {
    fun undo(expr: Expr): Term = when (expr) {
        is BoolExpr -> undoBool(expr)
        is BitVecNum -> undoBV(expr)
        is BitVecExpr -> undoBVExpr(expr)
        is FPNum -> undoFloat(expr)
        else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
    }

    private fun undoBVExpr(expr: BitVecExpr): Term = when {
        expr.numArgs == 2 && expr.args[0] is FPRMNum -> {
            val arg = undo(expr.args[1])
            when (expr.args[0] as FPRMNum) {
                // todo: support all modes
                else -> when (arg) {
                    is ConstFloatTerm -> term { const(arg.value.toInt()) }
                    is ConstDoubleTerm -> term { const(arg.value.toInt()) }
                    else -> arg
                }
            }
        }
        expr.isBVExtract -> undo(expr.args[0])
        expr.isBVNOT -> {
            when (val value = undo(expr.args[0])) {
                is ConstIntTerm -> term { const((value.value).inv()) }
                is ConstLongTerm -> term { const((value.value).inv()) }
                else -> TODO()
            }
        }
        expr.isApp -> when {
            expr.funcDecl.name.toString() == "fp.to_ieee_bv" -> {
                when (val fp = undo(expr.args[0])) {
                    is ConstFloatTerm -> term { const((fp.value).toInt()) }
                    is ConstDoubleTerm -> term { const((fp.value).toLong()) }
                    else -> TODO()
                }
            }
            expr.isBVAdd -> {
                val arg1 = undo(expr.args[0])
                val arg2 = undo(expr.args[1])
                term { const(arg1.numericValue + arg2.numericValue) }
            }
            expr.isBVSub -> {
                val arg1 = undo(expr.args[0])
                val arg2 = undo(expr.args[1])
                term { const(arg1.numericValue - arg2.numericValue) }
            }
            expr.isBVMul -> {
                val arg1 = undo(expr.args[0])
                val arg2 = undo(expr.args[1])
                term { const(arg1.numericValue * arg2.numericValue) }
            }
            expr.isBVSDiv -> {
                val arg1 = undo(expr.args[0])
                val arg2 = undo(expr.args[1])
                term { const(arg1.numericValue / arg2.numericValue) }
            }
            expr.isITE -> {
                val cond = undo(expr.args[0]) as ConstBoolTerm
                if (cond.value) undo(expr.args[1]) else undo(expr.args[2])
            }
            expr.isBVConcat -> {
                val first = undo(expr.args[0]).numericValue
                val second = undo(expr.args[1]).numericValue
                term { const(first * (10.0.pow(second.toString().length)) + second) }
            }
            expr.isBVOR -> {
                val first = undo(expr.args[0]).numericValue
                val second = undo(expr.args[1]).numericValue
                term { const(first or second) }
            }
            expr.isBVAND -> {
                val first = undo(expr.args[0]).numericValue
                val second = undo(expr.args[1]).numericValue
                term { const(first and second) }
            }
            expr.isBVXOR -> {
                val first = undo(expr.args[0]).numericValue
                val second = undo(expr.args[1]).numericValue
                term { const(first xor second) }
            }
            else -> unreachable { log.error("Not implemented unlogic SMT operation: $expr") }
        }
        // todo: support more bv expressions
        else -> unreachable { log.error("Not implemented unlogic SMT operation: $expr") }
    }

    private fun undoBool(expr: BoolExpr) = when (expr.boolValue) {
        Z3_lbool.Z3_L_TRUE -> term { const(true) }
        Z3_lbool.Z3_L_FALSE -> term { const(false) }
        else -> when {
            expr.isBVSLE -> term { const(undo(expr.args[0]).numericValue <= undo(expr.args[1]).numericValue) }
            expr.isBVSLT -> term { const(undo(expr.args[0]).numericValue < undo(expr.args[1]).numericValue) }
            expr.isBVSGE -> term { const(undo(expr.args[0]).numericValue >= undo(expr.args[1]).numericValue) }
            expr.isBVSGT -> term { const(undo(expr.args[0]).numericValue > undo(expr.args[1]).numericValue) }
            expr.isEq -> term { const(undo(expr.args[0]).numericValue == undo(expr.args[1]).numericValue) }
            else -> unreachable { log.error("Trying to undo unknown") }
        }
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
        else -> term { const(expr.long.toInt()) }
    }

    private fun undoFloat(expr: FPNum): Term {
        val isDouble = (expr.eBits + expr.sBits) == SMTEngine.DWORD
        val termifier = { a: Double ->
            when {
                isDouble -> term { const(a) }
                else -> term { const(a.toFloat()) }
            }
        }
        return when {
            expr.isZero -> termifier(0.0)
            expr.isNaN -> termifier(Double.NaN)
            expr.isInf -> termifier(if (expr.isPositive) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY)
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