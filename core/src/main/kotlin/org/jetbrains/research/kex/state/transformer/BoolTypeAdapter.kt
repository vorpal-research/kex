package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.type.BoolType
import org.jetbrains.research.kfg.type.IntType

object BoolTypeAdapter : Transformer<BoolTypeAdapter> {
    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val lhv = predicate.lhv
        val rhv = predicate.rhv
        val type = predicate.type
        val loc = predicate.location
        if (rhv.type != lhv.type) {
            println("${predicate.print()}: ${lhv.type} and ${rhv.type}")
        }
        val res = when {
            lhv.type === BoolType && rhv.type === IntType -> pf.getEquality(lhv, tf.getCast(TF.getBoolType(), rhv), type, loc)
            lhv.type === IntType && rhv.type === BoolType -> pf.getEquality(lhv, tf.getCast(TF.getIntType(), rhv), type, loc)
            else -> predicate
        }
        return res
    }
}