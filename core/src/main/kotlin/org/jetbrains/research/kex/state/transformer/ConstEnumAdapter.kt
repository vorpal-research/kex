package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexInt
import org.jetbrains.research.kex.ktype.KexString
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.basic
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.StaticClassRefTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kthelper.collection.dequeOf

class ConstEnumAdapter(val context: ExecutionContext) : RecollectingTransformer<ConstEnumAdapter> {
    val cm get() = context.cm
    override val builders = dequeOf(StateBuilder())

    private val Term.isEnum get() = (this.type.getKfgType(cm.type) as? ClassType)?.klass?.isEnum ?: false


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

    override fun apply(ps: PredicateState): PredicateState {
        val enumAccesses = TermCollector
            .getFullTermSet(ps)
            .filterIsInstance<FieldTerm>()
            .filter { it.owner is StaticClassRefTerm && it.owner.isEnum }
            .groupBy { it.owner as StaticClassRefTerm }

        for ((staticClass, enumTerms) in enumAccesses) {
            val enumKlass = context.loader.loadClass(cm.type, staticClass.type)

            for (enumFieldTerm in enumTerms) {
                val enumField = enumKlass.getField(enumFieldTerm.fieldName)
                enumField.isAccessible = true
                val enumValue = enumField.get(null)

                currentBuilder += basic {
                    val enumLoad = generate(staticClass.type)
                    state {
                        enumLoad equality enumFieldTerm.load()
                    }
                    state {
                        enumLoad.field(KexString(), "name").store(const(getEnumName(enumValue)))
                    }
                    state {
                        enumLoad.field(KexInt(), "ordinal").store(const(getEnumOrdinal(enumValue)))
                    }
                }
            }
        }

        return super.apply(ps)
    }
}