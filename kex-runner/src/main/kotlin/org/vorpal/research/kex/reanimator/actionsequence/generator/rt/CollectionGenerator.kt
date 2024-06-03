package org.vorpal.research.kex.reanimator.actionsequence.generator.rt

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.generator.Generator
import org.vorpal.research.kex.reanimator.actionsequence.generator.GeneratorContext

class CollectionGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val generators = setOf(
        ArrayListGenerator(this),
        ArrayDequeGenerator(this),
        LinkedListGenerator(this),
        HashMapGenerator(this),
        HashSetGenerator(this),
        WrapperClassGenerator(this)
    )

    override fun supports(descriptor: Descriptor): Boolean = generators.any { it.supports(descriptor) }

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        getFromCache(descriptor)?.let { return it }
        val generator = generators.firstOrNull { it.supports(descriptor) } ?: fallback
        generator.generate(descriptor, generationDepth)
    }
}
