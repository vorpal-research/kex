package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.mocking.NonMockedDescriptors
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.smt.FinalDescriptorReanimator
import org.vorpal.research.kex.smt.InitialDescriptorReanimator
import org.vorpal.research.kex.smt.ModelReanimator
import org.vorpal.research.kex.smt.SMTModel
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.DefaultSwitchPredicate
import org.vorpal.research.kex.state.predicate.EqualityPredicate
import org.vorpal.research.kex.state.predicate.InequalityPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

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
        is EqualityPredicate -> checkTerms(
            path.lhv,
            path.rhv
        ) { a, b -> a.numericValue == b.numericValue }

        is InequalityPredicate -> checkTerms(
            path.lhv,
            path.rhv
        ) { a, b -> a.numericValue != b.numericValue }

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

class SMTModelALiasAnalysis<T>(
    private val generator: AbstractGenerator<T>
) : AliasAnalysis {
    override fun mayAlias(lhv: Term, rhv: Term): Boolean {
        return generator.reanimateTerm(lhv) == generator.reanimateTerm(rhv)
    }
}

fun generateFinalDescriptors(
    method: Method,
    ctx: ExecutionContext,
    model: SMTModel,
    state: PredicateState
): Parameters<Descriptor> {
    val generator = DescriptorGenerator(method, ctx, model, FinalDescriptorReanimator(model, ctx))
    generator.apply(state)
    return Parameters(
        generator.instance,
        generator.args.mapIndexed { index, arg ->
            arg ?: descriptor { default(method.argTypes[index].kexType.rtMapped) }
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
    val generator = DescriptorGenerator(method, ctx, model, FinalDescriptorReanimator(model, ctx))
    generator.apply(state)
    val params = setOfNotNull(
        generator.instance,
        *generator.args.mapIndexed { index, arg ->
            arg ?: descriptor { default(method.argTypes[index].kexType.rtMapped) }
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


private class InitialDescriptors(
    override val descriptors: Parameters<Descriptor>,
    private val generator: DescriptorGenerator,
) : NonMockedDescriptors {
    override val termToDescriptor: Map<Term, Descriptor> get() = generator.memory
    override val allDescriptors: Iterable<Descriptor> get() = generator.allValues

    override fun generateAllDescriptors() {
        generator.generateAll()
    }
}

fun generateInitialDescriptors(
    method: Method,
    ctx: ExecutionContext,
    model: SMTModel,
    state: PredicateState
): NonMockedDescriptors {
    val generator = DescriptorGenerator(method, ctx, model, InitialDescriptorReanimator(model, ctx))
    generator.apply(state)
    return InitialDescriptors(
        Parameters(
            generator.instance,
            generator.args.mapIndexed { index, arg ->
                arg ?: descriptor { default(method.argTypes[index].kexType) }
            },
            generator.staticFields
        ), generator
    )
}


fun generateInitialDescriptorsAndAA(
    method: Method,
    ctx: ExecutionContext,
    model: SMTModel,
    state: PredicateState
): Pair<Parameters<Descriptor>, AliasAnalysis> {
    val generator = DescriptorGenerator(method, ctx, model, InitialDescriptorReanimator(model, ctx))
    generator.apply(state)
    return Parameters(
        generator.instance,
        generator.args.mapIndexed { index, arg ->
            arg ?: descriptor { default(method.argTypes[index].kexType) }
        },
        generator.staticFields,
    ) to SMTModelALiasAnalysis(generator)
}
