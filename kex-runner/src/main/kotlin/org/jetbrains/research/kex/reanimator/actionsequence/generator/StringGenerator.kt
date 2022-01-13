package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexChar
import org.jetbrains.research.kex.reanimator.actionsequence.ActionList
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.ConstructorCall
import org.jetbrains.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.type.SystemTypeNames

class StringGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor.type.toString() == SystemTypeNames.stringClass

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()
        descriptor.reduce()

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val stringClass = context.cm.stringClass

        val valueDescriptor = descriptor["value", KexArray(KexChar())]
        if (valueDescriptor == null) {
            actionSequence += DefaultConstructorCall(stringClass)
            return actionSequence
        }
        val value = fallback.generate(valueDescriptor, generationDepth + 1)

        val constructor = stringClass.getMethod("<init>", MethodDesc(arrayOf(types.getArrayType(types.charType)), types.voidType))
        actionSequence += ConstructorCall(constructor, listOf(value))
        return actionSequence
    }
}
