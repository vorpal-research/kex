package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.NewPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.axiom
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kthelper.collection.dequeOf
import java.util.Deque

class NewFieldInitializer(val ctx: ExecutionContext) : RecollectingTransformer<NewFieldInitializer> {
    override val builders: Deque<StateBuilder> = dequeOf(StateBuilder())
    private lateinit var fields: Set<Field>
    private lateinit var strings: Set<Term>

    override fun apply(ps: PredicateState): PredicateState {
        strings = getConstStringMap(ps).values.toSet()
        fields = TermCollector { it is FieldTerm }
            .also { it.apply(ps) }
            .terms
            .mapTo(hashSetOf()) { (it as FieldTerm).unmappedKfgField(ctx.cm) }
        return super.apply(ps)
    }

    override fun transformNew(predicate: NewPredicate): Predicate {
        val lhv = predicate.lhv
        val kfgType = lhv.type.getKfgType(ctx.types)

        currentBuilder += predicate

        if (lhv in strings) return nothing()
        if (kfgType is ClassType && kfgType.klass.isEnum) return nothing()

        for (field in fields) {
            if (kfgType.isSubtypeOf(field.klass.asType)) {
                currentBuilder += axiom {
                    val fieldType = field.type.kexType
                    val fieldTerm = lhv.field(fieldType, field.name)
                    fieldTerm.initialize(default(fieldType))
                }
            }
        }

        return nothing()
    }
}
