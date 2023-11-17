package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexJavaClass
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.ClassAccessTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.ConstStringTerm
import org.vorpal.research.kex.state.term.InstanceOfTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kex.util.parseAsConcreteType
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.Type

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
fun hasClassAccesses(state: IncrementalPredicateState) = ClassAccessDetector().let {
    it.apply(state.state)
    for (query in state.queries) {
        it.apply(query.hardConstraints)
        for (softConstraint in query.softConstraints) {
            it.transform(softConstraint)
        }
    }
    it.hasClassAccess
}

fun hasClassAccesses(predicate: Predicate) = ClassAccessDetector().let {
    it.transform(predicate)
    it.hasClassAccess
}

class TypeCollector(
    val ctx: ExecutionContext,
    private val checkStringTypes: Boolean = false
) : Transformer<TypeCollector> {
    companion object {
        private val instanceOfCache = mutableMapOf<Set<Type>, Set<Class>>()
    }

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
        val kfgChecked = term.checkedType.getKfgType(ctx.types)
        val kfgCurrent = term.operand.type.getKfgType(ctx.types)
        addType(term.checkedType)
        if (!kfgCurrent.isSubtypeOfCached(kfgChecked) && kfgCurrent is ClassType && kfgChecked is ClassType) {
            val intersection = instanceOfCache.getOrPut(setOf(kfgChecked, kfgCurrent)) {
                val checkedSubtypes = instantiationManager.getAllConcreteSubtypes(kfgChecked.klass, ctx.accessLevel)
                val currentSubtypes = instantiationManager.getAllConcreteSubtypes(kfgCurrent.klass, ctx.accessLevel)
                currentSubtypes.intersect(checkedSubtypes)
            }
            intersection.forEach {
                addType(it.kexType)
            }
        }
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
            parseAsConcreteType(ctx.types, string)?.let {
                addType(it)
            }
        }
    }

    private fun addType(type: KexType) {
        if (type is KexArray) {
            addType(type.element)
        } else if (type is KexReference) {
            addType(type.reference)
        }
        types += type
    }
}

fun collectTypes(ctx: ExecutionContext, state: IncrementalPredicateState): Set<KexType> {
    val tc = TypeCollector(ctx, hasClassAccesses(state))
    tc.apply(state.state)
    for (query in state.queries) {
        tc.apply(query.hardConstraints)
        for (soft in query.softConstraints) {
            tc.transform(soft)
        }
    }
    return tc.types
}

fun collectTypes(ctx: ExecutionContext, ps: PredicateState): Set<KexType> {
    val tc = TypeCollector(ctx, hasClassAccesses(ps))
    tc.apply(ps)
    return tc.types
}

@Suppress("unused")
fun collectTypes(ctx: ExecutionContext, predicate: Predicate): Set<KexType> {
    val tc = TypeCollector(ctx, hasClassAccesses(predicate))
    tc.transform(predicate)
    return tc.types
}
