package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.ChoiceState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.term.CastTerm
import org.jetbrains.research.kex.state.term.InstanceOfTerm
import org.jetbrains.research.kex.state.term.NullTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.type.TypeFactory

enum class Nullability {
    UNKNOWN, NULLABLE, NON_NULLABLE
}

sealed class TypeInfo

data class NullabilityInfo(val nullability: Nullability) : TypeInfo()
data class CastTypeInfo(val type: KexType) : TypeInfo()

class TypeInfoMap(val inner: Map<Term, Set<TypeInfo>> = hashMapOf()) : Map<Term, Set<TypeInfo>> by inner {
    inline fun <reified T : TypeInfo> getInfo(term: Term): T? = inner[term]?.mapNotNull { it as? T }?.also {
        assert(it.size <= 1) { log.warn("A lot of type information ${T::class.qualifiedName} about $term: $it") }
    }?.firstOrNull()
}

class TypeInfoCollector(val model: SMTModel, val tf: TypeFactory) : Transformer<TypeInfoCollector> {
    private val typeInfos = mutableMapOf<Term, MutableMap<TypeInfo, PredicateState>>()
    private val cfgt = CFGTracker()

    val infos: TypeInfoMap
        get() = TypeInfoMap(
                typeInfos.map { (term, map) ->
                    val types = map.filter { checkPath(model, it.value) }.keys
                    val reducedTypes = run {
                        val nullabilityInfo = types.filterIsInstance<NullabilityInfo>()
                        val castInfo = types.filterIsInstance<CastTypeInfo>()
                        val reducedCastInfo = mutableSetOf<CastTypeInfo>()
                        val klasses = castInfo.map { (it.type as KexClass).getKfgClass(tf) }.toSet()
                        for (klass in klasses) {
                            if (klasses.any { it != klass && klass.isAncestorOf(it) }) continue
                            else reducedCastInfo += CastTypeInfo(tf.getRefType(klass).kexType)
                        }
                        (nullabilityInfo + reducedCastInfo).toSet()
                    }
                    if (reducedTypes.isEmpty()) null
                    else term to types
                }.filterNotNull().toMap()
        )

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
                val checkedType = CastTypeInfo(rhv.checkedType)
                val operand = rhv.operand
                val condition = cfgt.getDominatingPaths(predicate)
                val fullPath = condition + path { predicate.lhv equality true }

                val typeInfo = typeInfos.getOrPut(operand, ::mutableMapOf)
                val existingCond = typeInfo.getOrDefault(checkedType, emptyState())
                typeInfo[checkedType] = existingCond or fullPath
            }
            is CastTerm -> {
                val newType = CastTypeInfo(rhv.type)
                val operand = rhv.operand
                val condition = cfgt.getDominatingPaths(predicate)
                val stub = when {
                    condition.isEmpty() -> setOf(path { const(true) equality true })
                    else -> condition
                }

                // we can't do anything with primary type casts
                if (newType.type is KexIntegral || newType.type is KexReal) return predicate

                val typeInfo = typeInfos.getOrPut(operand, ::mutableMapOf)
                val existingCond = typeInfo.getOrDefault(newType, emptyState())
                typeInfo[newType] = existingCond or stub
            }
        }
        return super.transformEquality(predicate)
    }

    override fun transformInequality(predicate: InequalityPredicate): Predicate {
        when (predicate.rhv) {
            is NullTerm -> {
                val nullability = NullabilityInfo(Nullability.NON_NULLABLE)
                val condition = cfgt.getDominatingPaths(predicate)
                val lhv = predicate.lhv

                val typeInfo = typeInfos.getOrPut(lhv, ::mutableMapOf)
                val existingCond = typeInfo.getOrDefault(nullability, emptyState())
                typeInfo[nullability] = existingCond or condition
            }
        }
        return super.transformInequality(predicate)
    }
}

fun collectTypeInfos(model: SMTModel, tf: TypeFactory, ps: PredicateState): TypeInfoMap {
    val tic = TypeInfoCollector(model, tf)
    tic.apply(ps)
    return tic.infos
}