package org.vorpal.research.kex.smt.boolector

import org.vorpal.research.boolector.BitvecNode
import org.vorpal.research.boolector.BoolNode
import org.vorpal.research.boolector.BoolectorNode
import org.vorpal.research.kex.smt.SMTEngine
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.TermFactory
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

object BoolectorUnlogic {
    private val tf = TermFactory

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

