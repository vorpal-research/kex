package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.ArrayDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.actionsequence.*

class ArrayGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor is ArrayDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as? ArrayDescriptor ?: throw IllegalArgumentException()

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val elementType = descriptor.elementType.getKfgType(types)
        val lengthCall = PrimaryValue(
            descriptor.length
        )
        val array = NewArray(types.getArrayType(elementType), lengthCall)
        actionSequence += array

        for ((index, value) in descriptor.elements) {
            val indexCall = PrimaryValue(index)
            val arrayWrite = ArrayWrite(indexCall, fallback.generate(value, generationDepth + 1))
            actionSequence += arrayWrite
        }

        actionSequence
    }
}
