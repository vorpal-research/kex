package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.ktype.KexNull
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.UnknownSequence

class UnknownGenerator(override val context: GeneratorContext) : Generator {
    private val constantGenerator = ConstantGenerator(context)

    constructor(executionCtx: ExecutionContext, psa: PredicateStateAnalysis, visibilityLevel: Visibility)
            : this(GeneratorContext(executionCtx, psa, visibilityLevel))

    override fun supports(descriptor: Descriptor): Boolean = true

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = when {
//        is ConstantDescriptor -> constantGenerator.generate(descriptor, generationDepth)
        else -> UnknownSequence(
            "${descriptor.term}",
            descriptor.wrappedType,
            descriptor
        )
    }

    private val Descriptor.wrappedType get() = when {
        this.type is KexNull -> context.types.objectType
        else -> this.type.getKfgType(context.types)
    }
}