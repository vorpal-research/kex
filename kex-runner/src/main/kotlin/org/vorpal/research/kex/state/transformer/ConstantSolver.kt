package org.vorpal.research.kex.state.transformer

import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.ChoiceState
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.CastTerm
import org.vorpal.research.kex.state.term.CmpTerm
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.ConstByteTerm
import org.vorpal.research.kex.state.term.ConstCharTerm
import org.vorpal.research.kex.state.term.ConstDoubleTerm
import org.vorpal.research.kex.state.term.ConstFloatTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.ConstLongTerm
import org.vorpal.research.kex.state.term.ConstShortTerm
import org.vorpal.research.kex.state.term.NullTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kthelper.and
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.compareTo
import org.vorpal.research.kthelper.div
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.minus
import org.vorpal.research.kthelper.or
import org.vorpal.research.kthelper.plus
import org.vorpal.research.kthelper.rem
import org.vorpal.research.kthelper.shl
import org.vorpal.research.kthelper.shr
import org.vorpal.research.kthelper.times
import org.vorpal.research.kthelper.toBoolean
import org.vorpal.research.kthelper.ushr
import org.vorpal.research.kthelper.xor

class ConstantSolver : Transformer<ConstantSolver>, IncrementalTransformer {
    companion object {
        private const val EPS = 1e-5
    }

    private val constantValues = mutableMapOf<Term, Term>()
    var isSat = true
        private set

    infix fun Double.eq(other: Double) = (this - other) < EPS
    private infix fun Double.neq(other: Double) = (this - other) >= EPS
    infix fun Float.eq(other: Float) = (this - other) < EPS
    private infix fun Float.neq(other: Float) = (this - other) >= EPS

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            ConstantPropagator.apply(state.state),
            state.queries.map { query ->
                PredicateQuery(
                    ConstantPropagator.apply(query.hardConstraints),
                    query.softConstraints.map { ConstantPropagator.transform(it) }.toPersistentList()
                )
            }
        )
    }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldIsSat = isSat
        var result = false
        for (choice in ps.choices) {
            isSat = oldIsSat
            super.transformBase(choice)
            result = result || isSat
        }
        isSat = result
        return ps
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val lhv = getConstantValue(term.lhv)?.numericValue ?: return term
        val rhv = getConstantValue(term.rhv)?.numericValue ?: return term
        constantValues[term] = term {
            when (term.opcode) {
                BinaryOpcode.ADD -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv + nRhv)
                }

                BinaryOpcode.SUB -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv - nRhv)
                }

                BinaryOpcode.MUL -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv * nRhv)
                }

                BinaryOpcode.DIV -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv / nRhv)
                }

                BinaryOpcode.REM -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv % nRhv)
                }

                BinaryOpcode.SHL -> const(lhv.shl(rhv as Int))
                BinaryOpcode.SHR -> const(lhv.shr(rhv as Int))
                BinaryOpcode.USHR -> const(lhv.ushr(rhv as Int))
                BinaryOpcode.AND -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv and nRhv)
                }

                BinaryOpcode.OR -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv or nRhv)
                }

                BinaryOpcode.XOR -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv, rhv)
                    const(nLhv xor nRhv)
                }
            }
        }
        return term
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val operand = getConstantValue(term.operand) ?: return term
        when (operand) {
            is NullTerm -> constantValues[term] = operand

            else -> {
                val numericValue = operand.numericValue
                constantValues[term] = term {
                    when (term.type) {
                        is KexBool -> const(numericValue.toBoolean())
                        is KexByte -> const(numericValue.toByte())
                        is KexChar -> const(numericValue.toChar())
                        is KexShort -> const(numericValue.toShort())
                        is KexInt -> const(numericValue.toInt())
                        is KexLong -> const(numericValue.toLong())
                        is KexFloat -> const(numericValue.toFloat())
                        is KexDouble -> const(numericValue.toDouble())
                        else -> operand
                    }
                }
            }
        }
        return term
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val lhv = getConstantValue(term.lhv) ?: return term
        val rhv = getConstantValue(term.rhv) ?: return term
        constantValues[term] = term {
            when (lhv.type) {
                is KexPointer -> when (term.opcode) {
                    CmpOpcode.EQ -> const(lhv == rhv)
                    CmpOpcode.NEQ -> const(lhv != rhv)
                    else -> unreachable { log.error("Unexpected cmp opcode for pointers: ${term.opcode}") }
                }
                else -> {
                    val (nLhv, nRhv) = toCompatibleTypes(lhv.numericValue, rhv.numericValue)
                    when (term.opcode) {
                        CmpOpcode.EQ -> when (nLhv) {
                            is Double -> const(nLhv eq nRhv.toDouble())
                            is Float -> const(nLhv eq nRhv.toFloat())
                            else -> const(nLhv == nRhv)
                        }

                        CmpOpcode.NEQ -> when (nLhv) {
                            is Double -> const(nLhv neq nRhv.toDouble())
                            is Float -> const(nLhv neq nRhv.toFloat())
                            else -> const(nLhv != nRhv)
                        }

                        CmpOpcode.LT -> const(nLhv < nRhv)
                        CmpOpcode.GT -> const(nLhv > nRhv)
                        CmpOpcode.LE -> const(nLhv <= nRhv)
                        CmpOpcode.GE -> const(nLhv >= nRhv)
                        CmpOpcode.CMP -> const(nLhv.compareTo(nRhv))
                        CmpOpcode.CMPG -> const(nLhv.compareTo(nRhv))
                        CmpOpcode.CMPL -> const(nLhv.compareTo(nRhv))
                    }
                }
            }
        }
        return term
    }

    private fun toCompatibleTypes(lhv: Number, rhv: Number): Pair<Number, Number> = when (lhv) {
        is Long -> lhv to (rhv as Long)
        is Float -> lhv to (rhv as Float)
        is Double -> lhv to (rhv as Double)
        else -> lhv.toInt() to rhv.toInt()
    }

    private fun getConstantValue(term: Term): Term? = when (term) {
        in constantValues -> constantValues[term]!!
        is ConstBoolTerm -> term
        is ConstByteTerm -> term
        is ConstCharTerm -> term
        is ConstDoubleTerm -> term
        is ConstFloatTerm -> term
        is ConstIntTerm -> term
        is ConstLongTerm -> term
        is ConstShortTerm -> term
        is NullTerm -> term
        else -> null
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {
                val rhv = getConstantValue(predicate.rhv) ?: return predicate
                constantValues[predicate.lhv] = rhv
            }
            else -> {
                val lhv = getConstantValue(predicate.lhv) ?: return predicate
                val rhv = getConstantValue(predicate.rhv) ?: return predicate
                if (lhv != rhv) {
                    isSat = false
                }
            }
        }
        return predicate
    }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {} // nothing
            else -> {
                val lhv = getConstantValue(predicate.lhv) ?: return predicate
                val rhv = getConstantValue(predicate.rhv) ?: return predicate
                if (lhv == rhv) {
                    isSat = false
                }
            }
        }
        return predicate
    }
}


fun tryConstantSolve(state: PredicateState, query: PredicateState): Result? {
    val solver = ConstantSolver()
    solver.apply(state)
    solver.apply(query)
    return when {
        solver.isSat -> null
        else -> Result.UnsatResult
    }
}
