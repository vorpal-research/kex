package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.predicate.assume
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.collection.dequeOf

class BasicInvariantsTransformer(
    val method: Method
) : RecollectingTransformer<BasicInvariantsTransformer> {
    override val builders = dequeOf(StateBuilder())

    override fun apply(ps: PredicateState): PredicateState {
        val thisType = method.klass.kexType.rtMapped
        val thisTerm = run {
            val (thisTerm, _) = collectArguments(ps)
            when {
                thisTerm != null -> thisTerm
                !method.isStatic -> term { `this`(thisType) }
                else -> null
            }
        }
        if (thisTerm != null) {
            currentBuilder += assume { thisTerm inequality null }
            currentBuilder += assume { (thisTerm `is` thisType) equality true }
        }
        return super.apply(ps)
    }
}
