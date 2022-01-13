package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence

interface Generator {
    val context: GeneratorContext

    fun supports(descriptor: Descriptor): Boolean
    fun generate(descriptor: Descriptor, generationDepth: Int = 0): ActionSequence
}