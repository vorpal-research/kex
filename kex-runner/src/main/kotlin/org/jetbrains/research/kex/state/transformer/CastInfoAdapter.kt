package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.assume
import org.jetbrains.research.kex.state.term.CastTerm
import org.jetbrains.research.kex.state.term.InstanceOfTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.type.TypeFactory
import java.util.*

class CastInfoAdapter(val tf: TypeFactory) : RecollectingTransformer<CastInfoAdapter> {
    override val builders = ArrayDeque<StateBuilder>().apply { add(StateBuilder()) }
    // term -> mapOf(type -> condition)
    val instanceOfInfo = mutableMapOf<Term, MutableMap<KexClass, Term>>()

    override fun transformEquality(predicate: EqualityPredicate): Predicate {
        when (val rhv = predicate.rhv) {
            is InstanceOfTerm -> {
                val checkedType = rhv.checkedType as KexClass
                val checkedKfgClass = checkedType.getKfgClass(tf)
                val operand = rhv.operand
                val instanceOfMap = instanceOfInfo.getOrPut(operand, ::mutableMapOf)

                for ((type, condition) in instanceOfMap) {
                    val kfgClass = type.getKfgClass(tf)
                    if (!(kfgClass.isAncestor(checkedKfgClass) || checkedKfgClass.isAncestor(kfgClass))) {
                        currentBuilder += assume { (predicate.lhv implies !condition) equality true }
                    }
                }
                instanceOfInfo.getOrPut(operand, ::mutableMapOf)[checkedType] = predicate.lhv
            }
            is CastTerm -> {
                val newType = rhv.type
                if (newType is KexClass) {
                    val newKfgClass = newType.getKfgClass(tf)
                    val operand = rhv.operand
                    val instanceOfMap = instanceOfInfo.getOrPut(operand, ::mutableMapOf)

                    for ((type, condition) in instanceOfMap) {
                        val kfgClass = type.getKfgClass(tf)
                        if (!(kfgClass.isAncestor(newKfgClass) || newKfgClass.isAncestor(kfgClass))) {
                            currentBuilder += assume { condition equality false }
                        }
                    }
                }
            }
        }
        return super.transformEquality(predicate)
    }
}