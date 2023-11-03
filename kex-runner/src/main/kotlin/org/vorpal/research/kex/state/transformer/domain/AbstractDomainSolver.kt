package org.vorpal.research.kex.state.transformer.domain

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.ChoiceState
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.NewArrayInitializerPredicate
import org.vorpal.research.kex.state.predicate.NewArrayPredicate
import org.vorpal.research.kex.state.predicate.NewInitializerPredicate
import org.vorpal.research.kex.state.predicate.NewPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.term.ArrayLengthTerm
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
import org.vorpal.research.kex.state.term.NegTerm
import org.vorpal.research.kex.state.term.NullTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.ConstantPropagator
import org.vorpal.research.kex.state.transformer.IncrementalTransformer
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.toInt

class AbstractDomainSolver : Transformer<AbstractDomainSolver>, IncrementalTransformer {
    private var abstractDomainValues = persistentMapOf<Term, DomainStorage>()

    var isSat: AbstractDomainValue = Top
        private set

    private fun storageValue(term: Term) = when (term) {
        in abstractDomainValues -> abstractDomainValues[term]!!
        else -> DomainStorage(
            when (term) {
                is ConstBoolTerm -> IntervalDomainValue(term.value.toInt())
                is ConstByteTerm -> IntervalDomainValue(term.value)
                is ConstCharTerm -> IntervalDomainValue(term.value.code)
                is ConstDoubleTerm -> IntervalDomainValue(term.value)
                is ConstFloatTerm -> IntervalDomainValue(term.value)
                is ConstIntTerm -> IntervalDomainValue(term.value)
                is ConstLongTerm -> IntervalDomainValue(term.value)
                is ConstShortTerm -> IntervalDomainValue(term.value)
                is NullTerm -> NullDomainValue
                else -> Top
            }
        ).also {
            abstractDomainValues = abstractDomainValues.put(term, it)
        }
    }

    private fun getDomainValue(term: Term): AbstractDomainValue = when (term) {
        in abstractDomainValues -> abstractDomainValues[term]!!.value
        is ConstBoolTerm -> IntervalDomainValue(term.value.toInt())
        is ConstByteTerm -> IntervalDomainValue(term.value)
        is ConstCharTerm -> IntervalDomainValue(term.value.code)
        is ConstDoubleTerm -> IntervalDomainValue(term.value)
        is ConstFloatTerm -> IntervalDomainValue(term.value)
        is ConstIntTerm -> IntervalDomainValue(term.value)
        is ConstLongTerm -> IntervalDomainValue(term.value)
        is ConstShortTerm -> IntervalDomainValue(term.value)
        is NullTerm -> NullDomainValue
        else -> Top
    }

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
        val oldConstValues = abstractDomainValues
        var result: AbstractDomainValue = Bottom
        val newConstantValues = mutableSetOf<PersistentMap<Term, DomainStorage>>()
        val allKeys = oldConstValues.keys.toMutableSet()
        for (choice in ps.choices) {
            isSat = oldIsSat
            abstractDomainValues = oldConstValues
            super.transformBase(choice)
            result = result.join(isSat)
            if (isSat !is UnsatDomainValue) {
                newConstantValues += abstractDomainValues
                allKeys += abstractDomainValues.keys
            }
        }
        isSat = result
        abstractDomainValues = oldConstValues
        for (key in allKeys) {
            storageValue(key).value = newConstantValues.mapNotNullTo(mutableSetOf()) { it[key]?.value }
                .reduceOrNull { acc, latticeValue -> acc.join(latticeValue) } ?: continue
        }
        return ps
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val lhv = getDomainValue(term.lhv)
        val rhv = getDomainValue(term.rhv)
        storageValue(term).value = lhv.apply(term.opcode, rhv)
        return term
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val operand = getDomainValue(term.operand)
        storageValue(term).value = operand.cast(term.type)
        return term
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val lhv = getDomainValue(term.lhv)
        val rhv = getDomainValue(term.rhv)
        storageValue(term).value = lhv.apply(term.opcode, rhv)
        return term
    }

    override fun transformNegTerm(term: NegTerm): Term {
        val operand = getDomainValue(term.operand)
        storageValue(term).value = operand.apply(UnaryOpcode.NEG)
        return term
    }

    override fun transformArrayLengthTerm(term: ArrayLengthTerm): Term {
        val operand = getDomainValue(term.arrayRef)
        storageValue(term).value = operand.apply(UnaryOpcode.LENGTH)
        return term
    }

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        storageValue(predicate.lhv).value = NonNullableDomainValue
        return predicate
    }

    override fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate {
        val positiveInterval = IntervalDomainValue(0, Int.MAX_VALUE)
        storageValue(predicate.lhv).value = when {
            predicate.dimensions.size != 1 -> ArrayDomainValue(
                NonNullableDomainValue,
                positiveInterval
            )

            else -> {
                val lengthValue = storageValue(predicate.dimensions.single())
                lengthValue.value = lengthValue.value.meet(positiveInterval)
                ArrayDomainValue(NonNullableDomainValue, lengthValue.value)
            }
        }
        return predicate
    }

    override fun transformNewInitializer(predicate: NewInitializerPredicate): Predicate {
        storageValue(predicate.lhv).value = NonNullableDomainValue
        return predicate
    }

    override fun transformNewArrayInitializerPredicate(predicate: NewArrayInitializerPredicate): Predicate {
        val positiveInterval = IntervalDomainValue(0, Int.MAX_VALUE)
        val lengthValue = storageValue(predicate.length)
        lengthValue.value = lengthValue.value.meet(positiveInterval)
        storageValue(predicate.lhv).value = ArrayDomainValue(NonNullableDomainValue, lengthValue.value)
        return predicate
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {
                val rhv = getDomainValue(predicate.rhv)
                storageValue(predicate.lhv).value = rhv
            }

            is PredicateType.Path -> {
                val lhv = getDomainValue(predicate.lhv)
                val rhv = getDomainValue(predicate.rhv)
                isSat = isSat.meet(lhv.satisfiesEquality(rhv))
            }

            else -> {
                val lhv = getDomainValue(predicate.lhv)
                val rhv = getDomainValue(predicate.rhv)
                isSat = isSat.meet(lhv.satisfiesEquality(rhv))
            }
        }
        return predicate
    }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {} // nothing
            else -> {
                val lhv = getDomainValue(predicate.lhv)
                val rhv = getDomainValue(predicate.rhv)
                isSat = isSat.meet(lhv.satisfiesInequality(rhv))
            }
        }
        return predicate
    }
}


fun tryAbstractDomainSolve(state: PredicateState, query: PredicateState): Result? {
    val solver = AbstractDomainSolver()
    solver.apply(state)
    solver.apply(query)
    return when (solver.isSat) {
        is Top -> null
        is SatDomainValue -> null
        is UnsatDomainValue -> Result.UnsatResult("abstract domain unsat")
        is Bottom -> Result.UnsatResult("abstract domain unsat")
        else -> unreachable { log.error("Unexpected satisfiability result from abstract domain solver: ${solver.isSat}") }
    }
}
