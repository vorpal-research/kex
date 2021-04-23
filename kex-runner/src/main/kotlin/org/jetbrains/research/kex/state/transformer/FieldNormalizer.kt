package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kthelper.collection.dequeOf

class FieldNormalizer(val cm: ClassManager, val prefix: String = ".normalized") : RecollectingTransformer<FieldNormalizer> {
    val types get() = cm.type
    override val builders = dequeOf(StateBuilder())
    private var counter = 0

    override fun transformFieldTerm(term: FieldTerm): Term {
        val kfgKlass = cm[term.klass] as? ConcreteClass ?: return term
        val field = kfgKlass.getField(term.fieldNameString, term.type.getKfgType(types))
        return when (field.`class`.fullname) {
            term.klass -> term
            else -> {
                val casted = term { value(field.`class`.kexType, "${term.owner.name}$prefix${counter++}") }
                currentBuilder += state { casted equality (term.owner `as` field.`class`.kexType) }
                term { casted.field(field.type.kexType, term.fieldNameString) }
            }
        }
    }
}