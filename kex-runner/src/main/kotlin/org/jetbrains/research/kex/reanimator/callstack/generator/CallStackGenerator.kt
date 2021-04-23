package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.UnknownCall
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.descriptor
import org.jetbrains.research.kthelper.KtException
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log

private val maxGenerationDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxGenerationDepth", 100) }
private val maxSearchDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxSearchDepth", 10000) }

class SearchLimitExceededException(val descriptor: Descriptor, msg: String) : KtException(msg)

class CallStackGenerator(override val context: GeneratorContext) : Generator {
    private val typeGenerators = mutableSetOf<Generator>()
    private var searchDepth = 0

    override fun supports(descriptor: Descriptor) = true

    init {
        typeGenerators += ConstantGenerator(context)
        typeGenerators += ArrayGenerator(this)
        typeGenerators += StaticFieldGenerator(this)
        typeGenerators += StringGenerator(this)
        typeGenerators += EnumGenerator(this)
        typeGenerators += KtObjectGenerator(this)
        typeGenerators += InnerClassGenerator(this)
        typeGenerators += CollectionGenerator(this)
        typeGenerators += MapGenerator(this)
        typeGenerators += AnyGenerator(this)
    }

    constructor(executionCtx: ExecutionContext, psa: PredicateStateAnalysis, visibilityLevel: Visibility)
            : this(GeneratorContext(executionCtx, psa, visibilityLevel))

    val Descriptor.generator: Generator
        get() = typeGenerators.firstOrNull { it.supports(this) } ?: unreachable {
            log.error("Could not find a generator for $descriptor")
        }

    fun generateDescriptor(descriptor: Descriptor): CallStack {
        searchDepth = 0
        val originalDescriptor = descriptor.deepCopy()
        return try {
            generate(descriptor)
        } catch (e: SearchLimitExceededException) {
            val name = "${originalDescriptor.term}"
            log.debug("Search limit exceeded: ${e.message}")
            log.debug(originalDescriptor)
            return UnknownCall(originalDescriptor.type.getKfgType(context.types), originalDescriptor).wrap(name)
        }
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
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
        typeGenerator.generate(descriptor, generationDepth + 1)

        return getFromCache(descriptor)!!
    }
}
