package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.smt.ModelReanimator
import org.jetbrains.research.kex.smt.ObjectReanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ir.Method

class ModelExecutor(
    override val method: Method,
    override val ctx: ExecutionContext,
    override val model: SMTModel
) : AbstractGenerator<Any?> {
    override val modelReanimator: ModelReanimator<Any?> = ObjectReanimator(method, model, ctx)

    override val memory = hashMapOf<Term, Any?>()

    override var thisTerm: Term? = null
    override val argTerms = sortedMapOf<Int, Term>()
    override val staticFieldOwners = mutableSetOf<Term>()

    override fun checkPath(path: Predicate): Boolean = when (path) {
        is EqualityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a == b }
        is InequalityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a != b }
        is DefaultSwitchPredicate -> {
            val lhv = path.cond
            val conditions = path.cases
            val lhvValue = reanimateTerm(lhv)
            val condValues = conditions.map { (it as ConstIntTerm).value }
            lhvValue !in condValues
        }
        else -> unreachable { log.error("Unexpected predicate in path: $path") }
    }
}

fun executeModel(
    ctx: ExecutionContext,
    ps: PredicateState,
    method: Method,
    model: SMTModel
): Parameters<Any?> {
    val pathExecutor = ModelExecutor(method, ctx, model)
    pathExecutor.apply(ps)
    return Parameters(pathExecutor.instance, pathExecutor.args, pathExecutor.staticFields)
}

fun generateInputByModel(
    ctx: ExecutionContext,
    method: Method,
    ps: PredicateState,
    model: SMTModel
): Parameters<Any?> {
    val reanimated = executeModel(ctx, ps, method, model)
    val loader = ctx.loader

    return when (reanimated.instance) {
        null -> reanimated.copy(instance = when {
            method.isStatic -> null
            else -> tryOrNull {
                val klass = loader.loadClass(method.`class`)
                ctx.random.next(klass)
            }
        })
        else -> reanimated
    }
}