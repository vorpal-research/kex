package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.ArrayLoadTerm
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.type.TypeFactory

// propagate all type infos backwards to arguments
class TypeInfoDFA(val tf: TypeFactory, val typeInfo: TypeInfoMap) : Transformer<TypeInfoDFA> {
    private val innerTypeInfo = typeInfo.map { it.key to it.value.toMutableSet() }.toMap().toMutableMap()

    val freshTypeInfo: TypeInfoMap get() = TypeInfoMap.create(tf, innerTypeInfo)

    override fun apply(ps: PredicateState): PredicateState {
        return super.apply(ps.reverse())
    }

    private fun mapInfos(from: Term, to: Term) {
        when (val nullability = typeInfo.getInfo<NullabilityInfo>(from)) {
            null -> {}
            else -> {
                innerTypeInfo.getOrPut(to, ::mutableSetOf).add(nullability)
            }
        }
        when (val typeInfo = typeInfo.getInfo<CastTypeInfo>(from)) {
            null -> {}
            else -> {
                innerTypeInfo.getOrPut(to, ::mutableSetOf).add(typeInfo)
            }
        }
    }

    override fun transformEquality(predicate: EqualityPredicate): Predicate {
        val lhv = predicate.lhv
        when (val rhv = predicate.rhv) {
            is FieldLoadTerm -> mapInfos(lhv, rhv.field)
            is ArrayLoadTerm -> mapInfos(lhv, rhv.arrayRef)
            else -> mapInfos(lhv, rhv)
        }
        return super.transformEquality(predicate)
    }

    override fun transformFieldInitializer(predicate: FieldInitializerPredicate): Predicate {
        mapInfos(predicate.field, predicate.value)
        return super.transformFieldInitializer(predicate)
    }

    override fun transformFieldStore(predicate: FieldStorePredicate): Predicate {
        mapInfos(predicate.field, predicate.value)
        return super.transformFieldStore(predicate)
    }

    override fun transformArrayStore(predicate: ArrayStorePredicate): Predicate {
        mapInfos(predicate.arrayRef, predicate.value)
        return super.transformArrayStore(predicate)
    }
}