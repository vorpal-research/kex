@file:Suppress("DuplicatedCode")

package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.ArrayInitializerPredicate
import org.vorpal.research.kex.state.predicate.ArrayStorePredicate
import org.vorpal.research.kex.state.predicate.FieldInitializerPredicate
import org.vorpal.research.kex.state.predicate.FieldStorePredicate
import org.vorpal.research.kex.state.predicate.NewArrayInitializerPredicate
import org.vorpal.research.kex.state.predicate.NewArrayPredicate
import org.vorpal.research.kex.state.predicate.NewInitializerPredicate
import org.vorpal.research.kex.state.predicate.NewPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.isConst
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.util.StringInfoContext

class ConstStringCollector : StringInfoContext(), Transformer<ConstStringCollector> {
    val strings = mutableMapOf<Term, MutableList<Char>>()
    private val valueArrays = mutableMapOf<Term, MutableList<Char>>()

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        if (predicate.lhv.type == KexString()) {
            strings[predicate.lhv] = mutableListOf()
        }
        return super.transformNewPredicate(predicate)
    }

    override fun transformNewInitializerPredicate(predicate: NewInitializerPredicate): Predicate {
        if (predicate.lhv.type == KexString()) {
            strings[predicate.lhv] = mutableListOf()
        }
        return super.transformNewInitializerPredicate(predicate)
    }

    override fun transformNewArray(predicate: NewArrayPredicate): Predicate {
        if (predicate.elementType == valueArrayType.element
            && predicate.numDimensions == 1
            && predicate.dimensions.first() is ConstIntTerm
        ) {
            valueArrays[predicate.lhv] = MutableList(predicate.dimensions.first().numericValue.toInt()) { ' ' }
        }
        return super.transformNewArray(predicate)
    }


    override fun transformNewArrayInitializer(predicate: NewArrayInitializerPredicate): Predicate {
        if (predicate.elementType == valueArrayType.element
            && predicate.length is ConstIntTerm
            && predicate.elements.all { it.isConst }
        ) {
            valueArrays[predicate.lhv] = predicate.elements.mapTo(mutableListOf()) { it.numericValue.toInt().toChar() }
        }
        return super.transformNewArrayInitializer(predicate)
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        val field = predicate.field as FieldTerm
        if (field.owner in strings && predicate.value in valueArrays) {
            strings[field.owner] = valueArrays[predicate.value]!!
        }
        return super.transformFieldStorePredicate(predicate)
    }

    override fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate {
        val field = predicate.field as FieldTerm
        if (field.owner in strings && predicate.value in valueArrays) {
            strings[field.owner] = valueArrays[predicate.value]!!
        }
        return super.transformFieldInitializerPredicate(predicate)
    }

    override fun transformArrayStore(predicate: ArrayStorePredicate): Predicate {
        val arrayIndex = predicate.arrayRef as ArrayIndexTerm
        if (arrayIndex.arrayRef in valueArrays
            && arrayIndex.index is ConstIntTerm
            && predicate.value.isConst
        ) {
            valueArrays[arrayIndex.arrayRef]!![arrayIndex.index.numericValue.toInt()] =
                predicate.value.numericValue.toInt().toChar()
        }
        return super.transformArrayStore(predicate)
    }

    override fun transformArrayInitializer(predicate: ArrayInitializerPredicate): Predicate {
        val arrayIndex = predicate.arrayRef as ArrayIndexTerm
        if (arrayIndex.arrayRef in valueArrays
            && arrayIndex.index is ConstIntTerm
            && predicate.value.isConst
        ) {
            valueArrays[arrayIndex.arrayRef]!![arrayIndex.index.numericValue.toInt()] =
                predicate.value.numericValue.toInt().toChar()
        }
        return super.transformArrayInitializer(predicate)
    }

}

fun getConstStringMap(ps: PredicateState): Map<String, Term> {
    val collector = ConstStringCollector()
    collector.apply(ps)
    return collector.strings.map { it.value.joinToString("") to it.key }.toMap()
}

fun getConstStringMap(state: IncrementalPredicateState): Map<String, Term> {
    val collector = ConstStringCollector()
    collector.apply(state.state)
    for (query in state.queries) {
        collector.apply(query.hardConstraints)
    }
    return collector.strings.map { it.value.joinToString("") to it.key }.toMap()
}
