package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kthelper.logging.log

class KexRtGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val typeGenerators = mutableSetOf<Generator>()

    init {
        typeGenerators += KexArrayListGenerator(this)
        typeGenerators += KexLinkedListGenerator(this)
        typeGenerators += KexWrapperClassGenerator(this)
        typeGenerators += KexStringBuilderGenerator(this)
    }

    override fun supports(descriptor: Descriptor): Boolean = typeGenerators.any { it.supports(descriptor) }

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence {
        val generator = typeGenerators.firstOrNull { it.supports(descriptor) } ?: fallback
        return generator.generate(descriptor, generationDepth)
    }
}