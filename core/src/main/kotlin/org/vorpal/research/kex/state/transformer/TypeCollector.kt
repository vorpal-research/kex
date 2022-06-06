package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexJavaClass
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kex.util.parseAsConcreteType
import org.vorpal.research.kfg.type.TypeFactory

private class ClassAccessDetector : Transformer<ClassAccessDetector> {
    var hasClassAccess = false
        private set

    override fun transformTerm(term: Term): Term {
        if (term.type == KexJavaClass()) hasClassAccess = true
        return super.transformTerm(term)
    }

    override fun transformClassAccessTerm(term: ClassAccessTerm): Term {
        hasClassAccess = true
        return super.transformClassAccessTerm(term)
    }
}

fun hasClassAccesses(ps: PredicateState) = ClassAccessDetector().let {
    it.apply(ps)
    it.hasClassAccess
}

class TypeCollector(val tf: TypeFactory, val checkStringTypes: Boolean = false) : Transformer<TypeCollector> {
    val types = mutableSetOf<KexType>()

    override fun apply(ps: PredicateState): PredicateState {
        val res = super.apply(ps)
        getConstStringMap(ps).keys.forEach {
            handleStringType(it)
        }
        return res
    }

    override fun transformTerm(term: Term): Term {
        addType(term.type)
        return super.transformTerm(term)
    }

    override fun transformInstanceOf(term: InstanceOfTerm): Term {
        addType(term.checkedType)
        return super.transformInstanceOf(term)
    }

    override fun transformConstClass(term: ConstClassTerm): Term {
        addType(term.constantType)
        return super.transformConstClass(term)
    }

    override fun transformConstString(term: ConstStringTerm): Term {
        handleStringType(term.value)
        return super.transformConstString(term)
    }

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        transform(term.body)
        return super.transformLambdaTerm(term)
    }

    private fun handleStringType(string: String) {
        if (checkStringTypes) {
            parseAsConcreteType(tf, string)?.let {
                addType(it)
            }
        }
    }

    private fun addType(type: KexType) {
        if (type is KexArray) {
            addType(type.element)
        }
        types += type
    }
}

fun collectTypes(tf: TypeFactory, ps: PredicateState): Set<KexType> {
    val tc = TypeCollector(tf, hasClassAccesses(ps))
    tc.apply(ps)
    return tc.types
}