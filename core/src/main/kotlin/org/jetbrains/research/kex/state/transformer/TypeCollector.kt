package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.ConstClassTerm
import org.jetbrains.research.kex.state.term.InstanceOfTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.getAllSubtypes
import org.jetbrains.research.kfg.type.TypeFactory

class TypeCollector(val tf: TypeFactory, val concretizeTypes: Boolean = false) : Transformer<TypeCollector> {
    val types = mutableSetOf<KexType>()

    override fun apply(ps: PredicateState): PredicateState {
        val res = super.apply(ps)
        if (concretizeTypes) {
            for (kfgType in types.map { it.getKfgType(tf) }.toSet()) {
                types += kfgType.getAllSubtypes(tf).map { it.kexType }
            }
        }
        return res
    }

    override fun transformTerm(term: Term): Term {
        types += term.type
        return super.transformTerm(term)
    }

    override fun transformInstanceOf(term: InstanceOfTerm): Term {
        types += term.checkedType
        return super.transformInstanceOf(term)
    }

    override fun transformConstClass(term: ConstClassTerm): Term {
        types += term.constantType
        return super.transformConstClass(term)
    }
}

fun collectTypes(tf: TypeFactory, ps: PredicateState, concretizeTypes: Boolean = false): Set<KexType> {
    val tc = TypeCollector(tf, concretizeTypes)
    tc.apply(ps)
    return tc.types
}