package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor

interface Generator {
    val context: GeneratorContext

    fun supports(type: KexType): Boolean
    fun generate(descriptor: Descriptor, generationDepth: Int = 0): CallStack
}