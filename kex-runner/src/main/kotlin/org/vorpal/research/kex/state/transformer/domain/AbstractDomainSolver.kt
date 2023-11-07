package org.vorpal.research.kex.state.transformer.domain

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.ExecutionContext
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
import org.vorpal.research.kex.state.term.InstanceOfTerm
import org.vorpal.research.kex.state.term.NegTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.transformer.ConstantPropagator
import org.vorpal.research.kex.state.transformer.IncrementalTransformer
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode

class AbstractDomainSolver(val ctx: ExecutionContext) : Transformer<AbstractDomainSolver>, IncrementalTransformer {
    private var abstractDomainValues = persistentMapOf<Term, DomainStorage>()
    private var equalities = persistentMapOf<Term, Term>()

    var satisfiabilityDomainValue: AbstractDomainValue = Top
        private set

    private fun storageValue(term: Term) = when (term) {
        in abstractDomainValues -> abstractDomainValues[term]!!
        else -> DomainStorage(DomainStorage.toDomainValue(ctx.types, term)).also {
            abstractDomainValues = abstractDomainValues.put(term, it)
        }
    }

    private fun domainValue(term: Term): AbstractDomainValue = when (term) {
        in abstractDomainValues -> abstractDomainValues[term]!!.value
        else -> DomainStorage.toDomainValue(ctx.types, term)
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

    private val PersistentMap<Term, DomainStorage>.deepCopy: PersistentMap<Term, DomainStorage>
        get() {
            return persistentMapOf<Term, DomainStorage>().builder().also {
                val tempMap = mutableMapOf<DomainStorage, DomainStorage>()
                for ((key, value) in this) {
                    it[key] = tempMap.getOrPut(value) { DomainStorage(value.value) }
                }
            }.build()
        }

    override fun transformChoice(ps: ChoiceState): PredicateState {
        val oldIsSat = satisfiabilityDomainValue
        val oldConstValues = abstractDomainValues
        val oldEqualities = equalities
        var result: AbstractDomainValue = Bottom

        val newEqualities = mutableSetOf<PersistentMap<Term, Term>>()
        val allEqualityKeys = equalities.keys.toMutableSet()

        val newDomainValues = mutableSetOf<PersistentMap<Term, DomainStorage>>()
        val allTermKeys = oldConstValues.keys.toMutableSet()
        for (choice in ps.choices) {
            satisfiabilityDomainValue = oldIsSat
            abstractDomainValues = oldConstValues.deepCopy
            equalities = oldEqualities
            super.transformBase(choice)
            result = result.join(satisfiabilityDomainValue)
            if (satisfiabilityDomainValue.isSat) {
                newEqualities += equalities
                allEqualityKeys += equalities.keys

                newDomainValues += abstractDomainValues
                allTermKeys += abstractDomainValues.keys
            }
        }
        satisfiabilityDomainValue = result

        equalities = oldEqualities.builder().also { builder ->
            for (key in allEqualityKeys) {
                newEqualities.mapNotNullTo(mutableSetOf()) { it[key] }.singleOrNull()?.let {
                    builder[key] = it
                }
            }
        }.build()

        abstractDomainValues = persistentMapOf()
        abstractDomainValues = oldConstValues.builder().also { builder ->
            for (key in allTermKeys) {
                builder.getOrPut(key) { DomainStorage(domainValue(key)) }.value =
                    newDomainValues.mapNotNullTo(mutableSetOf()) { it[key]?.value }
                        .reduceOrNull { acc, latticeValue -> acc.join(latticeValue) } ?: continue
            }
        }.build()
        return ps
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        val lhv = domainValue(term.lhv)
        val rhv = domainValue(term.rhv)
        storageValue(term).value = lhv.apply(term.opcode, rhv)
        return term
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val operand = domainValue(term.operand)
        val casted = operand.cast(term.type.getKfgType(ctx.types))
        storageValue(term).value = casted
        storageValue(term.operand).value = casted
        return term
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        val lhv = domainValue(term.lhv)
        val rhv = domainValue(term.rhv)
        storageValue(term).value = lhv.apply(term.opcode, rhv)
        return term
    }

    override fun transformInstanceOfTerm(term: InstanceOfTerm): Term {
        val operand = domainValue(term.operand)
        storageValue(term).value = operand.satisfiesType(term.checkedType.getKfgType(ctx.types))
        return term
    }

    override fun transformNegTerm(term: NegTerm): Term {
        val operand = domainValue(term.operand)
        storageValue(term).value = operand.apply(UnaryOpcode.NEG)
        return term
    }

    override fun transformArrayLengthTerm(term: ArrayLengthTerm): Term {
        val operand = domainValue(term.arrayRef)
        storageValue(term).value = operand.apply(UnaryOpcode.LENGTH)
        return term
    }

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        storageValue(predicate.lhv).value = DomainStorage.newPtr(ctx.types, predicate.lhv)
        return predicate
    }

    override fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate {
        storageValue(predicate.lhv).value = when {
            predicate.dimensions.size != 1 -> DomainStorage.newArray(ctx.types, predicate.lhv)

            else -> {
                val lengthValue = storageValue(predicate.dimensions.single())
                lengthValue.value = lengthValue.value.meet(ArrayDomainValue.TOP_LENGTH)
                DomainStorage.newArray(ctx.types, predicate.lhv, lengthValue.value)
            }
        }
        return predicate
    }

    override fun transformNewInitializer(predicate: NewInitializerPredicate): Predicate {
        storageValue(predicate.lhv).value = DomainStorage.newPtr(ctx.types, predicate.lhv)
        return predicate
    }

    override fun transformNewArrayInitializerPredicate(predicate: NewArrayInitializerPredicate): Predicate {
        val lengthValue = storageValue(predicate.length)
        lengthValue.value = lengthValue.value.meet(ArrayDomainValue.TOP_LENGTH)
        storageValue(predicate.lhv).value = DomainStorage.newArray(ctx.types, predicate.lhv, lengthValue.value)
        return predicate
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {
                val rhv = domainValue(predicate.rhv)
                storageValue(predicate.lhv).value = rhv
                equalities = equalities.put(predicate.lhv, predicate.rhv)
            }

            else -> {
                val lhv = domainValue(predicate.lhv)
                val rhv = domainValue(predicate.rhv)
                satisfiabilityDomainValue = satisfiabilityDomainValue.meet(lhv.satisfiesEquality(rhv))
            }
        }
        return predicate
    }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        when (predicate.type) {
            is PredicateType.State -> {} // nothing
            else -> {
                val lhv = domainValue(predicate.lhv)
                val rhv = domainValue(predicate.rhv)
                satisfiabilityDomainValue = satisfiabilityDomainValue.meet(lhv.satisfiesInequality(rhv))
            }
        }
        return predicate
    }
}


fun tryAbstractDomainSolve(ctx: ExecutionContext, state: PredicateState, query: PredicateState): Result? {
    val solver = AbstractDomainSolver(ctx)
    solver.apply(state)
    solver.apply(query)
    return when {
        solver.satisfiabilityDomainValue.isUnsat -> Result.UnsatResult("abstract domain unsat")
        else -> null
    }
}
