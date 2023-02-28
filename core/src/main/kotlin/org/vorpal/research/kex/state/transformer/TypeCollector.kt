package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.term.*
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
        if (!kfgCurrent.isSubtypeOf(kfgChecked) && kfgCurrent is ClassType && kfgChecked is ClassType) {
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

fun collectTypes(ctx: ExecutionContext, ps: PredicateState): Set<KexType> {
    val tc = TypeCollector(ctx, hasClassAccesses(ps))
    tc.apply(ps)
    return tc.types
}
