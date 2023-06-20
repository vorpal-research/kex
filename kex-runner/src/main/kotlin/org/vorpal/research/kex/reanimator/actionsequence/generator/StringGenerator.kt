package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.StringValue
import org.vorpal.research.kex.util.StringInfoContext
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

class StringGenerator(private val fallback: Generator) : StringInfoContext, Generator {
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) =
        (descriptor.type.toString() == SystemTypeNames.stringClass) && descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()
        descriptor.reduce()

        when (val valueDescriptor = descriptor[valueArrayName, valueArrayType] as? ArrayDescriptor) {
            null -> StringValue()
            else -> {
                val actualString = buildString {
                    for (i in 0 until valueDescriptor.length) {
                        val currentChar = when (i) {
                            in valueDescriptor.elements -> when (val element = valueDescriptor.elements[i]) {
                                is ConstantDescriptor.Char -> element.value
                                is ConstantDescriptor.Byte -> element.value.toInt().toChar()
                                else -> unreachable { log.error("Unexpected descriptor type in value array") }
                            }

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
