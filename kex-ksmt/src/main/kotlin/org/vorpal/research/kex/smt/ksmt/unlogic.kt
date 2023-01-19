package org.vorpal.research.kex.smt.ksmt

import org.ksmt.KContext
import org.ksmt.expr.*
import org.ksmt.solver.KModel
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.smt.ksmt.KSMTEngine.asExpr
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.boolValue
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.Transformer
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
            is KBitVec1Value -> const(expr.stringValue.toInt())
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
                val bounds = expr.bounds.map { it.asExpr(ctx) }
                    .associateWith { undo(model.eval(it, true), ctx, model, mappings) }
                undo(expr.body, ctx, model, mappings + bounds)
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

            is KEqExpr<*> -> {
                val lhv = undo(expr.lhs, ctx, model, mappings)
                val rhv = undo(expr.rhs, ctx, model, mappings)
                const(lhv == rhv)
            }

            is KIteExpr<*> -> {
                val cond = undo(expr.condition, ctx, model, mappings)
                when {
                    cond.boolValue -> undo(expr.trueBranch, ctx, model, mappings)
                    else -> undo(expr.falseBranch, ctx, model, mappings)
                }
            }

            is KBvExtractExpr -> {
                val value = undo(expr.value, ctx, model, mappings)
                val valueStr = when (expr.value.sort.sizeBits) {
                    64U -> value.numericValue.toLong().toString(2)
                    else -> value.numericValue.toInt().toString(2)
                }
                val paddedValue = valueStr.padStart(expr.value.sort.sizeBits.toInt(), '0').reversed()
                val extracted = paddedValue.substring(expr.low, expr.high - expr.low).reversed()
                term {
                    const(
                        when (expr.sort.sizeBits) {
                            64U -> extracted.toLong(2)
                            else -> extracted.toInt(2)
                        }
                    )
                }
            }

            is KArraySelect<*, *> -> {
                val array = undo(expr.array, ctx, model, mappings) as ArrayTerm
                when (val index = undo(expr.index, ctx, model, mappings)) {
                    in array.array -> array.array[index]!!
                    else -> array.default
                }
            }

            is KArrayStore<*, *> -> {
                val array = undo(expr.array, ctx, model, mappings) as ArrayTerm
                val index = undo(expr.index, ctx, model, mappings)
                val value = undo(expr.value, ctx, model, mappings)
                ArrayTerm(array.array + (index to value), array.default)
            }

            is KArrayConst<*, *> -> {
                val default = undo(expr.value, ctx, model, mappings)
                ArrayTerm(emptyMap(), default)
            }

            else -> unreachable { log.error("Unexpected expr in unlogic: $expr") }
        }
    }


    private class ArrayTerm(val array: Map<Term, Term>, val default: Term) : Term() {
        override val name: String = "ConstArray"
        override val subTerms: List<Term> = (array.keys + array.values).toList()
        override val type: KexType = KexNull()
        override fun <T : Transformer<T>> accept(t: Transformer<T>): Term {
            TODO("Not yet implemented")
        }

    }

}
