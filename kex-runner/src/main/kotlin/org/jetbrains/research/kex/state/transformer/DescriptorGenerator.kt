package org.jetbrains.research.kex.state.transformer

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.generator.ConstantDescriptor
import org.jetbrains.research.kex.generator.Descriptor
import org.jetbrains.research.kex.generator.descriptor
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.FinalDescriptorReanimator
import org.jetbrains.research.kex.smt.InitialDescriptorReanimator
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

class DescriptorGenerator(override val method: Method,
                          override val ctx: ExecutionContext,
                          override val model: SMTModel,
                          override val reanimator: Reanimator<Descriptor>) : AbstractGenerator<Descriptor> {

    override var typeInfos = TypeInfoMap()
    override val memory = hashMapOf<Term, Descriptor>()

    override var thisTerm: Term? = null
    override val argTerms = sortedMapOf<Int, Term>()

    override val javaClass = loader.loadClass(type.getRefType(method.`class`))
    override val javaMethod = when {
        method.isConstructor -> javaClass.getConstructor(method, loader)
        else -> javaClass.getMethod(method, loader)
    }

    override fun checkPath(path: Predicate): Boolean = when (path) {
        is EqualityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a == b }
        is InequalityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a != b }
        is DefaultSwitchPredicate -> {
            val lhv = path.cond
            val conditions = path.cases
            val lhvValue = (reanimateTerm(lhv) as ConstantDescriptor.Int).value
            val condValues = conditions.map { (it as ConstIntTerm).value }
            lhvValue !in condValues
        }
        else -> unreachable { log.error("Unexpected predicate in path: $path") }
    }
}

fun generateFinalDescriptors(method: Method, ctx: ExecutionContext, model: SMTModel, state: PredicateState): Pair<Descriptor?, List<Descriptor>> {
    val generator = DescriptorGenerator(method, ctx, model, FinalDescriptorReanimator(method, model, ctx))
    generator.apply(state)
    return generator.instance to generator.args.mapIndexed { index, arg ->
        arg ?: descriptor(ctx) { default(method.argTypes[index].kexType) }
    }
}

fun generateInitialDescriptors(method: Method, ctx: ExecutionContext, model: SMTModel, state: PredicateState): Pair<Descriptor?, List<Descriptor>> {
    val generator = DescriptorGenerator(method, ctx, model, InitialDescriptorReanimator(method, model, ctx))
    generator.apply(state)
    return generator.instance to generator.args.mapIndexed { index, arg ->
        arg ?: descriptor(ctx) { default(method.argTypes[index].kexType) }
    }
}