package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.KexPointer
import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.reanimator.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.descriptor
import org.jetbrains.research.kex.smt.FinalDescriptorReanimator
import org.jetbrains.research.kex.smt.InitialDescriptorReanimator
import org.jetbrains.research.kex.smt.ModelReanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.InequalityPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class DescriptorGenerator(
    override val method: Method,
    override val ctx: ExecutionContext,
    override val model: SMTModel,
    override val modelReanimator: ModelReanimator<Descriptor>
) : AbstractGenerator<Descriptor> {

    override val memory = hashMapOf<Term, Descriptor>()

    override var thisTerm: Term? = null
    override val argTerms = sortedMapOf<Int, Term>()
    override val staticFieldOwners = mutableSetOf<Term>()

    private val Any?.numericValue: Any?
        get() = when (this) {
            is ConstantDescriptor -> when (this) {
                is ConstantDescriptor.Bool -> this.value
                ConstantDescriptor.Null -> null
                is ConstantDescriptor.Byte -> this.value
                is ConstantDescriptor.Char -> this.value
                is ConstantDescriptor.Short -> this.value
                is ConstantDescriptor.Int -> this.value
                is ConstantDescriptor.Long -> this.value
                is ConstantDescriptor.Float -> this.value
                is ConstantDescriptor.Double -> this.value
            }
            else -> this
        }

    override fun checkPath(path: Predicate): Boolean = when (path) {
        is EqualityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a.numericValue == b.numericValue }
        is InequalityPredicate -> checkTerms(path.lhv, path.rhv) { a, b -> a.numericValue != b.numericValue }
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

fun generateFinalDescriptors(
    method: Method,
    ctx: ExecutionContext,
    model: SMTModel,
    state: PredicateState
): Parameters<Descriptor> {
    val generator = DescriptorGenerator(method, ctx, model, FinalDescriptorReanimator(method, model, ctx))
    generator.apply(state)
    return Parameters(
        generator.instance,
        generator.args.mapIndexed { index, arg ->
            arg ?: descriptor { default(method.argTypes[index].kexType) }
        },
        generator.staticFields
    )
}

fun generateFinalTypeInfoMap(
    method: Method,
    ctx: ExecutionContext,
    model: SMTModel,
    state: PredicateState
): TypeInfoMap {
    val generator = DescriptorGenerator(method, ctx, model, FinalDescriptorReanimator(method, model, ctx))
    generator.apply(state)
    val params = setOfNotNull(
        generator.instance,
        *generator.args.mapIndexed { index, arg ->
            arg ?: descriptor { default(method.argTypes[index].kexType) }
        }.toTypedArray(),
        *generator.staticFields.toTypedArray()
    )
    return TypeInfoMap(
        generator.memory
            .filterValues { candidate -> params.any { candidate in it } }
            .filterKeys { it.type is KexPointer }
            .mapValues {
                when (it.key.type) {
                    is KexReference -> setOf(CastTypeInfo(KexReference(it.value.type)))
                    else -> setOf(CastTypeInfo(it.value.type))
                }
            }
    )
}

fun generateInitialDescriptors(
    method: Method,
    ctx: ExecutionContext,
    model: SMTModel,
    state: PredicateState
): Parameters<Descriptor> {
    val generator = DescriptorGenerator(method, ctx, model, InitialDescriptorReanimator(method, model, ctx))
    generator.apply(state)
    return Parameters(
        generator.instance,
        generator.args.mapIndexed { index, arg ->
            arg ?: descriptor { default(method.argTypes[index].kexType) }
        },
        generator.staticFields
    )
}