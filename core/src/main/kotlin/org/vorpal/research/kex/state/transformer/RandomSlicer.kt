@file:Suppress("unused")

package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.state.term.ArrayContainsTerm
import org.vorpal.research.kex.state.term.ArrayLengthTerm
import org.vorpal.research.kex.state.term.ArrayLoadTerm
import org.vorpal.research.kex.state.term.BinaryTerm
import org.vorpal.research.kex.state.term.CastTerm
import org.vorpal.research.kex.state.term.CmpTerm
import org.vorpal.research.kex.state.term.ExistsTerm
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.ForAllTerm
import org.vorpal.research.kex.state.term.InstanceOfTerm
import org.vorpal.research.kex.state.term.IteTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kthelper.logging.log
import java.util.*

object RandomSlicer : Transformer<RandomSlicer> {
    private val random = Random(17)
    private var size = 0

    override fun transform(ps: PredicateState): PredicateState {
        size = ps.size
        return super.transform(ps).simplify()
    }

    override fun transformBase(predicate: Predicate): Predicate = when {
        random.nextDouble() < (2.0 / size) -> nothing()
        else -> predicate
    }
}

object RandomLambdaBodySlicer : Transformer<RandomSlicer> {
    private val random = Random(17)
    private var size = 0

    override fun transformLambda(term: LambdaTerm): Term {
        size = TermCollector.getFullTermSet(term.body).size
        return term{ lambda(term.type, term.parameters, super.transform(term.body)) }
    }

    override fun transformInstanceOf(term: InstanceOfTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { const(random.nextDouble() < 0.5) }
        else -> term
    }

    override fun transformArrayLength(term: ArrayLengthTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { const(random.nextInt(1000)) }
        else -> term
    }

    override fun transformArgument(term: ArgumentTerm): Term = term.accept(this)
    override fun transformIte(term: IteTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> when {
            random.nextDouble() < 0.5 -> transform(term.trueValue)
            else -> transform(term.falseValue)
        }

        else -> term{ ite(term.type, transform(term.cond), transform(term.trueValue), transform(term.falseValue)) }
    }

    override fun transformArrayLoad(term: ArrayLoadTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { generate(term.type) }
        else -> term{ transform(term.arrayRef).load() }
    }

    override fun transformFieldLoad(term: FieldLoadTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { generate(term.type) }
        else -> term{ transform(term.field).load() }
    }
    override fun transformBinary(term: BinaryTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> when (term.opcode) {
            BinaryOpcode.ADD -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }

            BinaryOpcode.SUB -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }

            BinaryOpcode.MUL -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }

            BinaryOpcode.DIV -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }

            BinaryOpcode.REM -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }

            BinaryOpcode.SHL -> term.lhv
            BinaryOpcode.SHR -> term.lhv
            BinaryOpcode.USHR -> term.lhv
            BinaryOpcode.AND -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }

            BinaryOpcode.OR -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }

            BinaryOpcode.XOR -> when {
                random.nextDouble() < 0.5 -> transform(term.lhv)
                else -> transform(term.rhv)
            }
        }

        else -> term{ transform(term.lhv).apply(term.type, term.opcode, transform(term.rhv)) }
    }

    override fun transformForAll(term: ForAllTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { const(random.nextDouble() < 0.5) }
        else -> term
    }

    override fun transformCast(term: CastTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { const(random.nextDouble() < 0.5) }
        else -> term
    }

    override fun transformCmp(term: CmpTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { const(random.nextDouble() < 0.5) }
        else -> term
    }

    override fun transformArrayContains(term: ArrayContainsTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { const(random.nextDouble() < 0.5) }
        else -> term
    }

    override fun transformExists(term: ExistsTerm): Term = when {
        random.nextDouble() < (2.0 / size) -> term { const(random.nextDouble() < 0.5) }
        else -> term
    }
}

class DeltaDebugger(
    private val attempts: Int,
    private val fails: Int = 10,
    val predicate: (PredicateState) -> Boolean
) {
    fun reduce(ps: PredicateState): PredicateState {
        var current = ps

        var failedAttempts = 0
        for (i in 0..attempts) {
            val reduced = run {
                var temp = RandomSlicer.apply(current)
                while (temp.size >= current.size && current.isNotEmpty)
                    temp = RandomSlicer.apply(current)
                temp
            }
            log.debug("Old size: ${current.size}, reduced size: ${reduced.size}")

            if (predicate(reduced)) {
                log.debug("Successful reduce")
                current = reduced
                failedAttempts = 0
            } else {
                log.debug("Reduce failed, rollback")
                ++failedAttempts
                if (failedAttempts > fails) {
                    log.debug("Too many failed attempts in a row, stopping reduction")
                    log.debug("Resulting state: {}", current)
                    break
                }
            }
        }

        log.debug("Reduced {}", ps)
        log.debug("To {}", current)
        return current
    }
}

class LambdaBodyDeltaDebugger(
    private val attempts: Int,
    private val fails: Int = 10,
    val predicate: (PredicateState) -> Boolean
) {
    fun reduce(ps: PredicateState): PredicateState {
        var current = ps

        var failedAttempts = 0
        for (i in 0..attempts) {
            val reduced = run {
                var temp = RandomLambdaBodySlicer.apply(current)
                while (TermCollector.getFullTermSet(temp).size >= TermCollector.getFullTermSet(current).size && current.isNotEmpty)
                    temp = RandomLambdaBodySlicer.apply(current)
                temp
            }
            log.debug("Old size: ${TermCollector.getFullTermSet(current).size}, reduced size: ${TermCollector.getFullTermSet(reduced).size}")

            if (predicate(reduced)) {
                log.debug("Successful reduce")
                current = reduced
                failedAttempts = 0
            } else {
                log.debug("Reduce failed, rollback")
                ++failedAttempts
                if (failedAttempts > fails) {
                    log.debug("Too many failed attempts in a row, stopping reduction")
                    log.debug("Resulting state: {}", current)
                    break
                }
            }
        }

        log.debug("Reduced {}", ps)
        log.debug("To {}", current)
        return current
    }
}

fun reduceState(ps: PredicateState, attempts: Int, predicate: (PredicateState) -> Boolean) =
    DeltaDebugger(attempts, attempts, predicate).reduce(ps)

fun reduceState(ps: PredicateState, attempts: Int, allowedFails: Int, predicate: (PredicateState) -> Boolean) =
    DeltaDebugger(attempts, allowedFails, predicate).reduce(ps)

fun reduceTermState(ps: PredicateState, attempts: Int, predicate: (PredicateState) -> Boolean) =
    LambdaBodyDeltaDebugger(attempts, attempts, predicate).reduce(ps)

fun reduceTermState(ps: PredicateState, attempts: Int, allowedFails: Int, predicate: (PredicateState) -> Boolean) =
    LambdaBodyDeltaDebugger(attempts, allowedFails, predicate).reduce(ps)
