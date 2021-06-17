package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.callstack.CallStack

interface Generator {
    val context: GeneratorContext

    fun supports(descriptor: Descriptor): Boolean
    fun generate(descriptor: Descriptor, generationDepth: Int = 0): CallStack
}