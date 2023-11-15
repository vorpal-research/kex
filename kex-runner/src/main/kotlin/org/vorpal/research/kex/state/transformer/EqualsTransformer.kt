package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.predicate
import org.vorpal.research.kex.state.predicate.receiver
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.EqualsTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.objectClass
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

class EqualsTransformer : Transformer<EqualsTransformer>, IncrementalTransformer {

    private val ClassManager.equalsMethod
        get() = objectClass.getMethod("equals", type.boolType, objectClass.asType)

    override fun transformCallTerm(term: CallTerm): Term {
        val method = term.method
        val cm = method.cm

        if (method != cm.equalsMethod) return term

        val lhv = term.owner
        val rhv = term.arguments.first()
        return term { lhv equls rhv }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate =
        when (val rhv = predicate.call) {
            is CallTerm -> predicate
            is EqualsTerm -> predicate(predicate.type, predicate.location) {
                predicate.receiver!! equality rhv
            }
            else -> unreachable { log.error("Unknown rhv in call predicate: $rhv") }
        }

    override fun transformLambda(term: LambdaTerm): Term {
        val body = transform(term.body)
        return term { lambda(term.type, term.parameters, body) }
    }

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            apply(state.state),
            state.queries
        )
    }
}
