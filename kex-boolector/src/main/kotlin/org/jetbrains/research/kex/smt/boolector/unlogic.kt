package org.jetbrains.research.kex.smt.boolector

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.boolector.BitvecNode
import org.jetbrains.research.boolector.BoolNode
import org.jetbrains.research.boolector.BoolectorNode
import org.jetbrains.research.kex.smt.SMTEngine
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory

object BoolectorUnlogic {
    val tf = TermFactory

    fun undo(expr: BoolectorNode): Term = when {
        expr.isBoolNode -> undoBool(expr.toBoolNode())
        expr.isBitvecNode -> undoBV(expr.toBitvecNode())
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
}

