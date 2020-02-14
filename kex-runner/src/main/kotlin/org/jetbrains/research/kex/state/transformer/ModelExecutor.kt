package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import com.abdullin.kthelper.tryOrNull
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.ObjectReanimator
import org.jetbrains.research.kex.smt.ReanimatedModel
import org.jetbrains.research.kex.smt.Reanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.getConstructor
import org.jetbrains.research.kex.util.getMethod
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ir.Method
import java.lang.reflect.Type

class ModelExecutor(override val method: Method,
                    override val ctx: ExecutionContext,
                    override val model: SMTModel) : AbstractGenerator<Any?> {
    override val reanimator: Reanimator<Any?> = ObjectReanimator(method, model, ctx)

    override var typeInfos = TypeInfoMap()
    override val memory = hashMapOf<Term, Any?>()

    override var thisTerm: Term? = null
    override val argTerms = sortedMapOf<Int, Term>()

    override val javaClass = loader.loadClass(type.getRefType(method.`class`))
    override val javaMethod = when {
        method.isConstructor -> javaClass.getConstructor(method, loader)
        else -> javaClass.getMethod(method, loader)
    }

    override fun generateThis() = thisTerm?.let {
        memory[it] = reanimator.reanimateNullable(it, javaClass)
    }

    override fun reanimateTerm(term: Term, jType: Type): Any? = reanimator.reanimateNullable(term, jType)

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

fun executeModel(ctx: ExecutionContext,
                 ps: PredicateState,
                 method: Method,
                 model: SMTModel): ReanimatedModel {
    val pathExecutor = ModelExecutor(method, ctx, model)
    pathExecutor.apply(ps)
    return ReanimatedModel(method, pathExecutor.instance, pathExecutor.args)
}

fun generateInputByModel(ctx: ExecutionContext,
                         method: Method,
                         ps: PredicateState,
                         model: SMTModel): Pair<Any?, Array<Any?>> {
    val reanimated = executeModel(ctx, ps, method, model)
    val loader = ctx.loader

    val instance = reanimated.instance ?: when {
        method.isStatic -> null
        else -> tryOrNull {
            val klass = loader.loadClass(ctx.types.getRefType(method.`class`))
            ctx.random.next(klass)
        }
    }

    if (instance == null && !method.isStatic) {
        throw GenerationException("Unable to create or generate instance of class ${method.`class`}")
    }
    return instance to reanimated.arguments.toTypedArray()
}