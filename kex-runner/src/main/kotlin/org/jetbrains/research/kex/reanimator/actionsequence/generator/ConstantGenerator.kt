package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.PrimaryValue
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class ConstantGenerator(override val context: GeneratorContext) : Generator {
    companion object {
        private var constNameIndex = 0
        private fun getConstName() = "const${constNameIndex++}"
    }
    override fun supports(descriptor: Descriptor) = descriptor is ConstantDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
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
        return stack
    }

}
