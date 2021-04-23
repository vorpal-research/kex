package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.FieldInitializerPredicate
import org.jetbrains.research.kex.state.predicate.FieldStorePredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.Field

val Term.isThis: Boolean get() = this.toString() == "this"

class FieldAccessCollector(val context: ExecutionContext) : Transformer<FieldAccessCollector> {
    val fieldAccesses = mutableSetOf<Field>()
    val fieldTerms = mutableSetOf<FieldTerm>()

    override fun transformFieldInitializer(predicate: FieldInitializerPredicate): Predicate {
        val fieldTerm = predicate.field as? FieldTerm ?: unreachable { log.error("Unexpected term in field store") }
        val klass = context.cm[fieldTerm.klass]
        val field = klass.getField(fieldTerm.fieldNameString, fieldTerm.type.getKfgType(context.types))
        if (fieldTerm.isStatic || fieldTerm.owner.isThis) {
            fieldAccesses += field
            fieldTerms += fieldTerm
        }
        return super.transformFieldInitializer(predicate)
    }

    override fun transformFieldStore(predicate: FieldStorePredicate): Predicate {
        val fieldTerm = predicate.field as? FieldTerm ?: unreachable { log.error("Unexpected term in field store") }
        val klass = context.cm[fieldTerm.klass]
        val field = klass.getField(fieldTerm.fieldNameString, fieldTerm.type.getKfgType(context.types))
        if (fieldTerm.isStatic || fieldTerm.owner.isThis) {
            fieldAccesses += field
            fieldTerms += fieldTerm
        }
        return super.transformFieldStore(predicate)
    }

    override fun transformFieldLoad(term: FieldLoadTerm): Term {
        val fieldTerm = term.field as? FieldTerm ?: unreachable { log.error("Unexpected term in field load") }
        if (fieldTerm.isStatic || fieldTerm.owner.isThis) {
            fieldTerms += fieldTerm
        }
        return super.transformFieldLoad(term)
    }
}

fun collectFieldAccesses(context: ExecutionContext, state: PredicateState): Set<Field> {
    val accessCollector = FieldAccessCollector(context)
    accessCollector.apply(state)
    return accessCollector.fieldAccesses
}

fun collectFieldTerms(context: ExecutionContext, state: PredicateState): Set<Term> {
    val accessCollector = FieldAccessCollector(context)
    accessCollector.apply(state)
    return accessCollector.fieldTerms
}