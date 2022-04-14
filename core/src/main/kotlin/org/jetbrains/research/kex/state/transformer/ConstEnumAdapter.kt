package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.ktype.KexString
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.basic
import org.jetbrains.research.kex.state.predicate.axiom
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.term.TermBuilder.Terms.field
import org.jetbrains.research.kex.util.allFields
import org.jetbrains.research.kex.util.isStatic
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kthelper.tryOrNull

class ConstEnumAdapter(val context: ExecutionContext) : RecollectingTransformer<ConstEnumAdapter> {
    val cm get() = context.cm
    override val builders = dequeOf(StateBuilder())

    private val Term.isEnum get() = (this.type.getKfgType(cm.type) as? ClassType)?.klass?.isEnum ?: false

    private fun getEnumFields(klass: Class<*>): List<Any> =
        klass.allFields
            .filter { it.isStatic && it.isEnumConstant }
            .map {
                it.isAccessible = true
                it.get(null)
            }

    private fun getEnumName(obj: Any): String {
        val nameField = obj.javaClass.superclass.getDeclaredField("name")
        nameField.isAccessible = true
        return nameField.get(obj) as String
    }

    private fun getEnumOrdinal(obj: Any): Int {
        val ordinalField = obj.javaClass.superclass.getDeclaredField("ordinal")
        ordinalField.isAccessible = true
        return ordinalField.getInt(obj)
    }

    private fun prepareEnumConstants(ps: PredicateState): Map<KexType, Set<Term>> {
        val enumClasses = TermCollector
            .getFullTermSet(ps)
            .asSequence()
            .filterIsInstance<FieldTerm>()
            .map { it.owner }
            .filterIsInstance<StaticClassRefTerm>()
            .filter { it.isEnum }
            .toList()

        val enumFields = mutableMapOf<KexType, Set<Term>>()

        for (staticClass in enumClasses) {
            tryOrNull {
                val enumKlass = context.loader.loadClass(cm.type, staticClass.type)
                val fields = mutableSetOf<Term>()

                for (enumField in getEnumFields(enumKlass)) {
                    val enumName = getEnumName(enumField)
                    val enumOrdinal = getEnumOrdinal(enumField)
                    val enumFieldTerm = staticClass.field(staticClass.type, enumName)

                    currentBuilder += basic {
                        val enumLoad = generate(staticClass.type)
                        state {
                            enumLoad equality enumFieldTerm.load()
                        }
                        state {
                            enumLoad.field(KexString(), "name").store(const(enumName))
                        }
                        state {
                            enumLoad.field(KexInt(), "ordinal").store(const(enumOrdinal))
                        }
                        fields += enumLoad
                    }
                }
                enumFields[staticClass.type] = fields
            }
        }
        return enumFields
    }

    private fun mapEnumTerms(ps: PredicateState, enumConstantsMap: Map<KexType, Set<Term>>) {
        val enumValueTerms = TermCollector
            .getFullTermSet(ps)
            .asSequence()
            .filter { it.isEnum }
            .filterNot { it.type is KexReference }
            .filterNot { it is FieldLoadTerm }
            .filterNot { it is StaticClassRefTerm }
            .filterNot { it in enumConstantsMap.getOrElse(it.type, ::setOf) }
            .toList()

        for (enumValueTerm in enumValueTerms) {
            var constraint: Term? = null
            for (constant in enumConstantsMap.getOrElse(enumValueTerm.type, ::setOf)) {
                constraint = when (constraint) {
                    null -> term { enumValueTerm eq constant }
                    else -> term { constraint!! or (enumValueTerm eq constant) }
                }
            }

            constraint?.apply {
                currentBuilder += axiom { constraint equality true }
            }
        }
    }

    override fun apply(ps: PredicateState): PredicateState {
        val enumConstantsMap = prepareEnumConstants(ps)
        super.apply(ps)
        mapEnumTerms(ps, enumConstantsMap)
        return state.simplify()
    }
}