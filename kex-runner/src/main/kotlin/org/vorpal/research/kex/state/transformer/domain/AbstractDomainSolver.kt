package org.vorpal.research.kex.state.transformer.domain

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
import org.vorpal.research.kex.state.transformer.ConstantPropagator
import org.vorpal.research.kex.state.transformer.IncrementalTransformer
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.toInt

class AbstractDomainSolver : Transformer<AbstractDomainSolver>, IncrementalTransformer {
    private var abstractDomainValues = mutableMapOf<Term, AbstractDomainValue>()

    var isSat: AbstractDomainValue = Top
        private set

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
        val oldConstValues = abstractDomainValues.toMap()
        var result: AbstractDomainValue = Bottom
        val newConstantValues = mutableSetOf<Map<Term, AbstractDomainValue>>()
        val allKeys = oldConstValues.keys.toMutableSet()
        for (choice in ps.choices) {
            isSat = oldIsSat
            abstractDomainValues = oldConstValues.toMutableMap()
            super.transformBase(choice)
            result = result.join(isSat)
            if (isSat !is UnsatDomainValue) {
                newConstantValues += abstractDomainValues
                allKeys += abstractDomainValues.keys
            }
        }
        isSat = result
        abstractDomainValues = mutableMapOf()
        for (key in allKeys) {
            abstractDomainValues[key] = newConstantValues.mapNotNullTo(mutableSetOf()) { it[key] }
                .reduceOrNull { acc, latticeValue -> acc.join(latticeValue) } ?: continue
        }
        return ps
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val lhv = getLatticeValue(term.lhv)
        val rhv = getLatticeValue(term.rhv)
        abstractDomainValues[term] = lhv.apply(term.opcode, rhv)
        return term
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val operand = getLatticeValue(term.operand)
        abstractDomainValues[term] = operand.cast(term.type)
        return term
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val lhv = getLatticeValue(term.lhv)
        val rhv = getLatticeValue(term.rhv)
        abstractDomainValues[term] = lhv.apply(term.opcode, rhv)
        return term
    }

    private fun getLatticeValue(term: Term): AbstractDomainValue = when (term) {
        in abstractDomainValues -> abstractDomainValues[term]!!
        is ConstBoolTerm -> ConstantDomainValue(term.value.toInt())
        is ConstByteTerm -> ConstantDomainValue(term.value)
        is ConstCharTerm -> ConstantDomainValue(term.value.code)
        is ConstDoubleTerm -> ConstantDomainValue(term.value)
        is ConstFloatTerm -> ConstantDomainValue(term.value)
        is ConstIntTerm -> ConstantDomainValue(term.value)
        is ConstLongTerm -> ConstantDomainValue(term.value)
        is ConstShortTerm -> ConstantDomainValue(term.value)
        is NullTerm -> NullDomainValue
        else -> Top
    }

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        abstractDomainValues[predicate.lhv] = NonNullableDomainValue
        return predicate
    }

    override fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate {
        abstractDomainValues[predicate.lhv] = NonNullableDomainValue
        return predicate
    }

    override fun transformNewInitializer(predicate: NewInitializerPredicate): Predicate {
        abstractDomainValues[predicate.lhv] = NonNullableDomainValue
        return predicate
    }

    override fun transformNewArrayInitializerPredicate(predicate: NewArrayInitializerPredicate): Predicate {
        abstractDomainValues[predicate.lhv] = NonNullableDomainValue
        return predicate
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {
                val rhv = getLatticeValue(predicate.rhv)
                abstractDomainValues[predicate.lhv] = rhv
            }

            else -> {
                val lhv = getLatticeValue(predicate.lhv)
                val rhv = getLatticeValue(predicate.rhv)
                isSat = isSat.meet(lhv.satisfiesEquality(rhv))
            }
        }
        return predicate
    }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {} // nothing
            else -> {
                val lhv = getLatticeValue(predicate.lhv)
                val rhv = getLatticeValue(predicate.rhv)
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
        is Bottom -> Result.UnsatResult("abstract domain")
        else -> unreachable { log.error("Unexpected satisfiability result from abstract domain solver: ${solver.isSat}") }
    }
}
