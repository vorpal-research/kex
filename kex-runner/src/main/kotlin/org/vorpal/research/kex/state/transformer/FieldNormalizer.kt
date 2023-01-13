package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kthelper.collection.dequeOf
import org.vorpal.research.kthelper.tryOrNull

class FieldNormalizer(
    val cm: ClassManager,
    private val prefix: String = ".normalized"
) : RecollectingTransformer<FieldNormalizer> {
    val types get() = cm.type
    override val builders = dequeOf(StateBuilder())
    private var counter = 0

    override fun transformFieldTerm(term: FieldTerm): Term {
        val field = tryOrNull { term.unmappedKfgField(cm) } ?: return term
        return when (field.klass.fullName) {
            term.klass -> term
            else -> {
                val casted = term { value(field.klass.kexType, "${term.owner.name}$prefix${counter++}") }
                currentBuilder += state { casted equality (term.owner `as` field.klass.kexType) }
                term { casted.field(field.type.kexType, term.fieldName) }
            }
        }
    }
}
