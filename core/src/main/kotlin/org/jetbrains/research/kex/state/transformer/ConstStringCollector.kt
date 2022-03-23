package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.KexChar
import org.jetbrains.research.kex.ktype.KexString
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*

class ConstStringCollector : Transformer<ConstStringCollector> {
    val strings = mutableMapOf<Term, MutableList<Char>>()
    val charArrays = mutableMapOf<Term, MutableList<Char>>()

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        if (predicate.lhv.type == KexString()) {
            strings[predicate.lhv] = mutableListOf()
        }
        return super.transformNewPredicate(predicate)
    }

    override fun transformNewArray(predicate: NewArrayPredicate): Predicate {
        if (predicate.elementType is KexChar && predicate.numDimensions == 1 && predicate.dimensions.first() is ConstIntTerm) {
            charArrays[predicate.lhv] = MutableList(predicate.dimensions.first().numericValue.toInt()) { ' ' }
        }
        return super.transformNewArray(predicate)
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        val field = predicate.field as FieldTerm
        if (field.owner in strings && predicate.value in charArrays) {
            strings[field.owner] = charArrays[predicate.value]!!
        }
        return super.transformFieldStorePredicate(predicate)
    }

    override fun transformArrayStore(predicate: ArrayStorePredicate): Predicate {
        val arrayIndex = predicate.arrayRef as ArrayIndexTerm
        if (arrayIndex.arrayRef in charArrays && arrayIndex.index is ConstIntTerm && predicate.value is ConstCharTerm) {
            charArrays[arrayIndex.arrayRef]!![arrayIndex.index.numericValue.toInt()] = predicate.value.numericValue.toChar()
        }
        return super.transformArrayStore(predicate)
    }

}

fun getConstStringMap(ps: PredicateState): Map<String, Term> {
    val collector = ConstStringCollector()
    collector.apply(ps)
    return collector.strings.map { it.value.joinToString("") to it.key }.toMap()
}