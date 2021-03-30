package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.reanimator.callstack.ArrayWrite
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.NewArray
import org.jetbrains.research.kex.reanimator.callstack.PrimaryValue
import org.jetbrains.research.kex.reanimator.descriptor.ArrayDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor

class ArrayGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor is ArrayDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as? ArrayDescriptor ?: throw IllegalArgumentException()

        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        saveToCache(descriptor, callStack)

        val elementType = descriptor.elementType.getKfgType(types)
        val lengthCall = PrimaryValue(descriptor.length)
        val array = NewArray(types.getArrayType(elementType), lengthCall)
        callStack += array

        descriptor.elements.forEach { (index, value) ->
            val indexCall = PrimaryValue(index)
            val arrayWrite = ArrayWrite(indexCall, fallback.generate(value, generationDepth + 1))
            callStack += arrayWrite
        }

        callStack
    }
}
