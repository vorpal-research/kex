package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.PrimaryValue
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

class ConstantGenerator(override val context: GeneratorContext) : Generator {
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
