package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.PrimaryValue
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class ConstantGenerator(override val context: GeneratorContext) : Generator {
    companion object {
        private var constNameIndex = 0
        private fun getConstName() = "const${constNameIndex++}"
    }
    override fun supports(descriptor: Descriptor) = descriptor is ConstantDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        val stack = when (descriptor) {
            is ConstantDescriptor.Null -> PrimaryValue(getConstName(), null, types.objectType)
            is ConstantDescriptor.Bool -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            is ConstantDescriptor.Byte -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            is ConstantDescriptor.Char -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            is ConstantDescriptor.Short -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            is ConstantDescriptor.Int -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            is ConstantDescriptor.Long -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            is ConstantDescriptor.Float -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            is ConstantDescriptor.Double -> PrimaryValue(getConstName(), descriptor.value, descriptor.type.getKfgType(types))
            else -> unreachable { log.error("Unknown descriptor in constant generator: $descriptor") }
        }
        return stack
    }

}
