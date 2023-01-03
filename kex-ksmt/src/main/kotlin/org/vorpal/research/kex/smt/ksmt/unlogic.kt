package org.vorpal.research.kex.smt.ksmt

import org.ksmt.KContext
import org.ksmt.expr.*
import org.ksmt.solver.KModel
import org.vorpal.research.kex.smt.ksmt.KSMTEngine.asExpr
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.compareTo
import org.vorpal.research.kthelper.logging.log

object KSMTUnlogic {

    fun undo(
        expr: KExpr<*>,
        ctx: KContext,
        model: KModel,
        mappings: Map<KExpr<*>, Term> = emptyMap()
    ): Term = term {
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
            is KExistentialQuantifier -> {
                val bounds = expr.bounds.map { it.asExpr(ctx) }
                    .associateWith { undo(model.eval(it, true), ctx, model, mappings) }
                undo(expr.body, ctx, model, mappings + bounds)
            }

            is KUniversalQuantifier -> {
                TODO()
            }
            
            in mappings -> mappings.getValue(expr)

            is KAndExpr -> {
                val lhv = undo(expr.args[0], ctx, model, mappings)
                val rhv = undo(expr.args[1], ctx, model, mappings)
                const(lhv.boolValue && rhv.boolValue)
            }

            is KOrExpr -> {
                val lhv = undo(expr.args[0], ctx, model, mappings)
                val rhv = undo(expr.args[1], ctx, model, mappings)
                const(lhv.boolValue || rhv.boolValue)
            }

            is KXorExpr -> {
                val lhv = undo(expr.args[0], ctx, model, mappings)
                val rhv = undo(expr.args[1], ctx, model, mappings)
                const(lhv.boolValue xor rhv.boolValue)
            }

            is KBvSignedLessExpr<*> -> {
                val lhv = undo(expr.args[0], ctx, model, mappings)
                val rhv = undo(expr.args[1], ctx, model, mappings)
                const(lhv.numericValue < rhv.numericValue)
            }

            is KBvSignedLessOrEqualExpr<*> -> {
                val lhv = undo(expr.args[0], ctx, model, mappings)
                val rhv = undo(expr.args[1], ctx, model, mappings)
                const(lhv.numericValue <= rhv.numericValue)
            }

            is KBvSignedGreaterExpr<*> -> {
                val lhv = undo(expr.args[0], ctx, model, mappings)
                val rhv = undo(expr.args[1], ctx, model, mappings)
                const(lhv.numericValue > rhv.numericValue)
            }

            is KBvSignedGreaterOrEqualExpr<*> -> {
                val lhv = undo(expr.args[0], ctx, model, mappings)
                val rhv = undo(expr.args[1], ctx, model, mappings)
                const(lhv.numericValue >= rhv.numericValue)
            }

            is KNotExpr -> const(!undo(expr.args[0], ctx, model, mappings).boolValue)

            else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
        }
    }

}
