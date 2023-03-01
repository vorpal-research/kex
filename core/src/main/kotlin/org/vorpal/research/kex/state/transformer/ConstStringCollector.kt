package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.*
import org.vorpal.research.kex.state.term.*

class ConstStringCollector : Transformer<ConstStringCollector> {
    val strings = mutableMapOf<Term, MutableList<Char>>()
    private val charArrays = mutableMapOf<Term, MutableList<Char>>()

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
        if (predicate.elementType is KexChar
            && predicate.numDimensions == 1
            && predicate.dimensions.first() is ConstIntTerm
        ) {
            charArrays[predicate.lhv] = MutableList(predicate.dimensions.first().numericValue.toInt()) { ' ' }
        }
        return super.transformNewArray(predicate)
    }

    override fun transformNewArrayInitializer(predicate: NewArrayInitializerPredicate): Predicate {
        if (predicate.elementType is KexChar
            && predicate.length is ConstIntTerm
            && predicate.elements.all { it is ConstCharTerm }
        ) {
            charArrays[predicate.lhv] = predicate.elements.mapTo(mutableListOf()) { (it as ConstCharTerm).value }
        }
        return super.transformNewArrayInitializer(predicate)
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        val field = predicate.field as FieldTerm
        if (field.owner in strings && predicate.value in charArrays) {
            strings[field.owner] = charArrays[predicate.value]!!
        }
        return super.transformFieldStorePredicate(predicate)
    }

    override fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate {
        val field = predicate.field as FieldTerm
        if (field.owner in strings && predicate.value in charArrays) {
            strings[field.owner] = charArrays[predicate.value]!!
        }
        return super.transformFieldInitializerPredicate(predicate)
    }

    override fun transformArrayStore(predicate: ArrayStorePredicate): Predicate {
        val arrayIndex = predicate.arrayRef as ArrayIndexTerm
        if (arrayIndex.arrayRef in charArrays
            && arrayIndex.index is ConstIntTerm
            && predicate.value is ConstCharTerm
        ) {
            charArrays[arrayIndex.arrayRef]!![arrayIndex.index.numericValue.toInt()] =
                predicate.value.numericValue.toChar()
        }
        return super.transformArrayStore(predicate)
    }

    override fun transformArrayInitializer(predicate: ArrayInitializerPredicate): Predicate {
        val arrayIndex = predicate.arrayRef as ArrayIndexTerm
        if (arrayIndex.arrayRef in charArrays
            && arrayIndex.index is ConstIntTerm
            && predicate.value is ConstCharTerm
        ) {
            charArrays[arrayIndex.arrayRef]!![arrayIndex.index.numericValue.toInt()] =
                predicate.value.numericValue.toChar()
        }
        return super.transformArrayInitializer(predicate)
    }

}

fun getConstStringMap(ps: PredicateState): Map<String, Term> {
    val collector = ConstStringCollector()
    collector.apply(ps)
    return collector.strings.map { it.value.joinToString("") to it.key }.toMap()
}
