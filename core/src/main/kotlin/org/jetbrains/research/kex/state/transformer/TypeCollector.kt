package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.term.ConstClassTerm
import org.jetbrains.research.kex.state.term.InstanceOfTerm
import org.jetbrains.research.kex.state.term.Term

class TypeCollector : Transformer<TypeCollector> {
    val types = mutableSetOf<KexType>()

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

fun collectTypes(ps: PredicateState): Set<KexType> {
    val tc = TypeCollector()
    tc.apply(ps)
    return tc.types
}