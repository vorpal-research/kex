package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexIntegral
import org.jetbrains.research.kex.ktype.KexReal
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.term.CastTerm
import org.jetbrains.research.kex.state.term.InstanceOfTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.log

class TypeInfoCollector(val model: SMTModel) : Transformer<TypeInfoCollector> {
    private val typeInfos = mutableMapOf<Term, MutableMap<KexType, PredicateState>>()
    private val cfgt = CFGTracker()

    val infos: Map<Term, KexType>
        get() = typeInfos.map { (term, map) ->
            val types = map.filter { checkPath(model, it.value) }.keys
            if (types.size > 1) log.warn("A lot of type information about $term: $types")
            val type = types.firstOrNull()
            when {
                type != null -> term to type
                else -> null
            }
        }.filterNotNull().toMap()

    private infix fun PredicateState.or(preds: Set<Predicate>): PredicateState {
        return ChoiceState(listOf(this, BasicState(preds.toList()))).simplify()
    }

    override fun apply(ps: PredicateState): PredicateState {
        cfgt.apply(ps)
        return super.apply(ps)
    }

    override fun transformEquality(predicate: EqualityPredicate): Predicate {
        when (val rhv = predicate.rhv) {
            is InstanceOfTerm -> {
                val checkedType = rhv.checkedType
                val operand = rhv.operand
                val condition = cfgt.getDominatingPaths(predicate)
                val fullPath = condition + path { predicate.lhv equality true }

                val typeInfo = typeInfos.getOrPut(operand, ::mutableMapOf)
                val existingCond = typeInfo.getOrDefault(checkedType, emptyState())
                typeInfo[checkedType] = existingCond or fullPath
            }
            is CastTerm -> {
                val newType = rhv.type
                val operand = rhv.operand
                val condition = cfgt.getDominatingPaths(predicate)
                val stub = when {
                    condition.isEmpty() -> setOf(path { const(true) equality true })
                    else -> condition
                }

                // we can't do anything with primary type casts
                if (newType is KexIntegral || newType is KexReal) return predicate

                val typeInfo = typeInfos.getOrPut(operand, ::mutableMapOf)
                val existingCond = typeInfo.getOrDefault(newType, emptyState())
                typeInfo[newType] = existingCond or stub
            }
        }
        return super.transformEquality(predicate)
    }
}

fun collectTypeInfos(model: SMTModel, ps: PredicateState): Map<Term, KexType> {
    val tic = TypeInfoCollector(model)
    tic.apply(ps)
    return tic.infos
}