package org.vorpal.research.kex.state.transformer

import kotlinx.collections.immutable.toPersistentList
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
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
) : RecollectingTransformer<FieldNormalizer>, IncrementalTransformer {
    val types get() = cm.type
    override val builders = dequeOf(StateBuilder())
    private var counter = 0
    val remapping = mutableMapOf<Term, Term>()

    override fun transformFieldTerm(term: FieldTerm): Term {
        val field = tryOrNull { term.unmappedKfgField(cm) } ?: return term
        return when (field.klass.fullName) {
            term.klass -> term
            else -> {
                val casted = term { value(field.klass.kexType.rtMapped, "${term.owner.name}$prefix${counter++}") }
                currentBuilder += state { casted equality (term.owner `as` field.klass.kexType.rtMapped) }
                remapping[term.owner] = casted
                term { casted.field(field.type.kexType.rtMapped, term.fieldName) }
            }
        }
    }

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            apply(state.state),
            state.queries.map { query ->
                PredicateQuery(
                    apply(query.hardConstraints),
                    query.softConstraints.map { TermRemapper(remapping).transform(it) }.toPersistentList()
                )
            }
        )
    }
}
