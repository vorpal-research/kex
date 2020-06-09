package org.jetbrains.research.kex.smt.stp

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.zhekehz.stpjava.*

object STPUnlogic {
    val tf = TermFactory

    fun undo(expr: Expr): Term = when {
        expr.sort.isBool -> undoBool(expr.asBool().counterExample)
        expr.sort.isBitVector -> undoBV(expr.asBitVector().counterExample)
        else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
    }

    private fun undoBool(expr: BoolExpr) = when (expr.toBoolean()) {
        true -> tf.getTrue()
        false -> tf.getFalse()
    }

    private fun undoBV(expr: BitVectorExpr) = when (expr.width) {
        SMTEngine.WORD -> tf.getInt(expr.toInt())
        SMTEngine.DWORD -> tf.getLong(expr.toLong())
        else -> unreachable { log.error("Trying to undo bv with unexpected size: ${expr.sort}") }
    }
}

