package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.*
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
import org.jetbrains.research.kfg.type.TypeFactory

class TypeInfoCollector(val model: SMTModel, val tf: TypeFactory) : Transformer<TypeInfoCollector> {
    private val typeInfos = mutableMapOf<Term, MutableMap<KexType, PredicateState>>()
    private val cfgt = CFGTracker()

    val infos: Map<Term, KexType>
        get() = typeInfos.map { (term, map) ->
            val types = map.filter { checkPath(model, it.value) }.keys
            val reducedTypes = run {
                val result = mutableSetOf<KexType>()
                val klasses = types.map { (it as KexClass).getKfgClass(tf) }.toSet()
                for (klass in klasses) {
                    if (klasses.any { it != klass && klass.isAncestor(it) }) continue
                    else result += tf.getRefType(klass).kexType
                }
                result
            }
            if (reducedTypes.size > 1)
                log.warn("A lot of type information about $term: $reducedTypes")
            val type = reducedTypes.firstOrNull()
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

fun collectTypeInfos(model: SMTModel, tf: TypeFactory, ps: PredicateState): Map<Term, KexType> {
    val tic = TypeInfoCollector(model, tf)
    tic.apply(ps)
    return tic.infos
}