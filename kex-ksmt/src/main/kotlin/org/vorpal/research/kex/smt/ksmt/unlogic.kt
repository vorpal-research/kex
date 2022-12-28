package org.vorpal.research.kex.smt.ksmt

import org.ksmt.expr.*
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

object KSMTUnlogic {

    fun undo(expr: KExpr<*>): Term = term {
        when (expr) {
            is KTrue -> const(true)
            is KFalse -> const(false)
            is KBitVec8Value -> const(expr.numberValue)
            is KBitVec16Value -> const(expr.numberValue)
            is KBitVec32Value -> const(expr.numberValue)
            is KBitVec64Value -> const(expr.numberValue)
            is KBitVecCustomValue -> const(expr.binaryStringValue)
            is KFp32Value -> const(expr.value)
            is KFp64Value -> const(expr.value)
            else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
        }
    }

}
