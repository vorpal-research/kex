package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.UnknownSequence
import org.vorpal.research.kex.reanimator.actionsequence.generator.kexrt.KexRtGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.CharsetGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.ClassGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.CollectionGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.FieldGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.StringGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.UnmodifiableCollectionGenerator
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log

class SearchLimitExceededException(val descriptor: Descriptor, msg: String) : KtException(msg)

class ActionSequenceGenerator(override val context: GeneratorContext) : Generator {
    private val maxGenerationDepth by lazy { kexConfig.getIntValue("reanimator", "maxGenerationDepth", 100) }
    private val maxSearchDepth by lazy { kexConfig.getIntValue("reanimator", "maxSearchDepth", 10000) }

    private val typeGenerators = mutableSetOf<Generator>()
    private var searchDepth = 0

    override fun supports(descriptor: Descriptor) = true

    init {
        typeGenerators += ConstantGenerator(context)
        typeGenerators += ArrayGenerator(this)
        typeGenerators += StaticFieldGenerator(this)
        typeGenerators += CharsetGenerator(this)
        typeGenerators += StringGenerator(this)
        typeGenerators += ClassGenerator(this)
        typeGenerators += MethodGenerator(this)
        typeGenerators += FieldGenerator(this)
        typeGenerators += EnumGenerator(this)
        typeGenerators += KtObjectGenerator(this)
        typeGenerators += InnerClassGenerator(this)
        typeGenerators += CollectionGenerator(this)
        typeGenerators += KexRtGenerator(this)
        typeGenerators += UnmodifiableCollectionGenerator(this)
        typeGenerators += ReanimatingCollectionGenerator(this)
        typeGenerators += ReanimatingMapGenerator(this)
        typeGenerators += AnyGenerator(this)
    }

    constructor(executionCtx: ExecutionContext, psa: PredicateStateAnalysis)
            : this(GeneratorContext(executionCtx, psa))

    val Descriptor.generator: Generator
        get() = typeGenerators.firstOrNull { it.supports(this) } ?: unreachable {
            log.error("Could not find a generator for $this")
        }

    fun generateDescriptor(descriptor: Descriptor): ActionSequence {
        searchDepth = 0
        val originalDescriptor = descriptor.deepCopy()
        return try {
            generate(descriptor)
        } catch (e: SearchLimitExceededException) {
            val name = "${originalDescriptor.term}"
            log.debug("Search limit exceeded: ${e.message}")
            log.debug(originalDescriptor)
            return UnknownSequence(name, originalDescriptor.type.getKfgType(context.types), originalDescriptor)
        }
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        getFromCache(descriptor)?.let { return it }
        searchDepth++

        if (generationDepth > maxGenerationDepth)
            throw SearchLimitExceededException(
                descriptor,
                "Generation depth exceeded maximal limit $maxGenerationDepth"
            )

        if (searchDepth > maxSearchDepth)
            throw SearchLimitExceededException(descriptor, "Search depth exceeded maximal limit $maxSearchDepth")

        val typeGenerator = descriptor.generator

        return typeGenerator.generate(descriptor, generationDepth + 1)
    }
}
