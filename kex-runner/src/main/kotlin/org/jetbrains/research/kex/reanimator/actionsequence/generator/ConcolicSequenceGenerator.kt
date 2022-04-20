package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.ktype.KexNull
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class ConcolicSequenceGenerator(override val context: GeneratorContext) : Generator {
    private val typeGenerators = mutableSetOf<Generator>()
    private var searchDepth = 0

    override fun supports(descriptor: Descriptor) = true


    init {
        typeGenerators += ConstantGenerator(context)
        typeGenerators += CharsetGenerator(this)
        typeGenerators += ClassGenerator(this)
        typeGenerators += FieldGenerator(this)
        typeGenerators += ReflectionEnumGenerator(this)
        typeGenerators += KexRtGenerator(this)
        typeGenerators += UnknownGenerator(this)
    }

    private val Descriptor.wrappedType get() = when (this.type) {
        is KexNull -> context.types.objectType
        else -> this.type.getKfgType(context.types)
    }

    constructor(executionCtx: ExecutionContext, psa: PredicateStateAnalysis, visibilityLevel: Visibility)
            : this(GeneratorContext(executionCtx, psa, visibilityLevel))

    val Descriptor.generator: Generator
        get() = typeGenerators.firstOrNull { it.supports(this) } ?: unreachable {
            log.error("Could not find a generator for $this")
        }

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        getFromCache(descriptor)?.let { return it }
        searchDepth++

        val typeGenerator = descriptor.generator

        return typeGenerator.generate(descriptor, generationDepth + 1)
    }
}