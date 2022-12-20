package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.reanimator.actionsequence.*
import org.vorpal.research.kfg.type.SystemTypeNames

class StringGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor.type.toString() == SystemTypeNames.stringClass

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()
        descriptor.reduce()

        when (val valueDescriptor = descriptor["value", KexArray(KexChar)] as? ArrayDescriptor) {
            null -> StringValue()
            else -> {
                val actualString = buildString {
                    for (i in 0 until valueDescriptor.length) {
                        val currentChar = when (i) {
                            in valueDescriptor.elements -> (valueDescriptor.elements[i] as ConstantDescriptor.Char).value
                            else -> ' '
                        }
                        append(currentChar)
                    }
                }
                StringValue(actualString)
            }
        }
    }
}
