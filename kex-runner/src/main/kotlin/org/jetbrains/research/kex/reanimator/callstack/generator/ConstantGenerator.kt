package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.PrimaryValue
import org.jetbrains.research.kex.reanimator.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class ConstantGenerator(override val context: GeneratorContext) : Generator {
    override fun supports(descriptor: Descriptor) = descriptor is ConstantDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        val stack = when (descriptor) {
            is ConstantDescriptor.Null -> PrimaryValue(null)
            is ConstantDescriptor.Bool -> PrimaryValue(descriptor.value)
            is ConstantDescriptor.Byte -> PrimaryValue(descriptor.value)
            is ConstantDescriptor.Char -> PrimaryValue(descriptor.value)
            is ConstantDescriptor.Short -> PrimaryValue(descriptor.value)
            is ConstantDescriptor.Int -> PrimaryValue(descriptor.value)
            is ConstantDescriptor.Long -> PrimaryValue(descriptor.value)
            is ConstantDescriptor.Float -> PrimaryValue(descriptor.value)
            is ConstantDescriptor.Double -> PrimaryValue(descriptor.value)
            else -> unreachable { log.error("Unknown descriptor in constant generator: $descriptor") }
        }
        saveToCache(descriptor, stack)
        return stack
    }

}