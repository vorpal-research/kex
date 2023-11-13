package org.vorpal.research.kex.state.transformer.domain

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexInteger
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexType
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
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.ConstantPropagator
import org.vorpal.research.kex.state.transformer.IncrementalTransformer
import org.vorpal.research.kex.state.transformer.Transformer
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.maxOf
import org.vorpal.research.kthelper.minOf

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
                equalities = equalities.put(predicate.lhv, equalities[predicate.rhv] ?: predicate.rhv)
            }

            else -> {
                val lhv = domainValue(predicate.lhv)
                val rhv = domainValue(predicate.rhv)
                satisfiabilityDomainValue = satisfiabilityDomainValue.meet(lhv.satisfiesEquality(rhv))
                if (satisfiabilityDomainValue.isSat) {
                    val condition = equalities[predicate.lhv] ?: predicate.lhv
                    when (condition) {
                        is CmpTerm -> when {
                            rhv.isTrue -> propagateCmpCondition(condition)
                            rhv.isFalse -> propagateCmpCondition(condition.reversed())
                        }

                        is InstanceOfTerm -> when {
                            rhv.isTrue -> propagateInstanceOfCondition(condition, true)
                            rhv.isFalse -> propagateInstanceOfCondition(condition, false)
                        }
                    }
                }
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
                if (satisfiabilityDomainValue.isSat) {
                    propagateCmpCondition(term { predicate.lhv neq predicate.rhv } as CmpTerm)
                }
            }
        }
        return predicate
    }

    private fun CmpTerm.reversed(): CmpTerm = term {
        when (opcode) {
            CmpOpcode.EQ -> lhv neq rhv
            CmpOpcode.NEQ -> lhv eq rhv
            CmpOpcode.LT -> lhv ge rhv
            CmpOpcode.GT -> lhv le rhv
            CmpOpcode.LE -> lhv gt rhv
            CmpOpcode.GE -> lhv lt rhv
            CmpOpcode.CMP -> rhv cmp lhv
            CmpOpcode.CMPG -> rhv cmpg lhv
            CmpOpcode.CMPL -> rhv cmpl lhv
        }
    } as CmpTerm

    private fun propagateCmpCondition(term: CmpTerm) {
        val lhv = storageValue(term.lhv)
        val rhv = storageValue(term.rhv)
        when {
            term.lhv.type is KexPointer && rhv.value.isNull -> when (term.opcode) {
                CmpOpcode.EQ -> lhv.value.assign(NullDomainValue)
                CmpOpcode.NEQ -> lhv.value.assign(NonNullableDomainValue)
                else -> {}
            }

            term.lhv.type is KexInteger && rhv.value.isConstant -> when (term.opcode) {
                CmpOpcode.EQ -> lhv.value.assign(rhv.value)
                CmpOpcode.NEQ -> {} // nothing
                CmpOpcode.LT, CmpOpcode.LE -> when (val lhvValue = lhv.value) {
                    is IntervalDomainValue<*> -> lhv.value.assign(
                        IntervalDomainValue(
                            lhvValue.min,
                            minOf(lhvValue.max, (rhv.value as IntervalDomainValue<*>).min)
                        )
                    )

                    is Top -> lhv.value.assign(
                        IntervalDomainValue(
                            lowerBound(term.lhv.type),
                            (rhv.value as IntervalDomainValue<*>).min
                        )
                    )

                    else -> {}
                }

                CmpOpcode.GT, CmpOpcode.GE -> when (val lhvValue = lhv.value) {
                    is IntervalDomainValue<*> -> lhv.value.assign(
                        IntervalDomainValue(
                            maxOf(lhvValue.min, (rhv.value as IntervalDomainValue<*>).min),
                            lhvValue.max
                        )
                    )

                    is Top -> lhv.value.assign(
                        IntervalDomainValue(
                            (rhv.value as IntervalDomainValue<*>).min,
                            upperBound(term.lhv.type)
                        )
                    )

                    else -> {}
                }

                else -> {}
            }
        }
    }

    private fun propagateInstanceOfCondition(term: InstanceOfTerm, positive: Boolean) {
        val operand = storageValue(term.operand)
        if (positive) {
            operand.value.assign(TypeDomainValue(term.checkedType.getKfgType(ctx.types)))
        }
    }

    private fun lowerBound(type: KexType): Number = when (type) {
        is KexBool -> 0
        is KexByte -> Byte.MAX_VALUE
        is KexChar -> Char.MIN_VALUE.code
        is KexShort -> Short.MIN_VALUE
        is KexInt -> Int.MIN_VALUE
        is KexLong -> Long.MIN_VALUE
        is KexFloat -> Float.NEGATIVE_INFINITY
        is KexDouble -> Double.NEGATIVE_INFINITY
        else -> unreachable { log.error("Unknown type $type for lower bound") }
    }

    private fun upperBound(type: KexType): Number = when (type) {
        is KexBool -> 1
        is KexByte -> Byte.MAX_VALUE
        is KexChar -> Char.MAX_VALUE.code
        is KexShort -> Short.MAX_VALUE
        is KexInt -> Int.MAX_VALUE
        is KexLong -> Long.MAX_VALUE
        is KexFloat -> Float.POSITIVE_INFINITY
        is KexDouble -> Double.POSITIVE_INFINITY
        else -> unreachable { log.error("Unknown type $type for lower bound") }
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
