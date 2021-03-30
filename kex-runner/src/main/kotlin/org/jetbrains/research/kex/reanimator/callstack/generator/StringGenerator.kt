package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexChar
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.ConstructorCall
import org.jetbrains.research.kex.reanimator.callstack.DefaultConstructorCall
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.ObjectDescriptor
import org.jetbrains.research.kfg.ir.MethodDesc

class StringGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor.type.toString() == "java/lang/String"

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()
        descriptor.reduce()

        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        saveToCache(descriptor, callStack)

        val stringClass = context.cm["java/lang/String"]

        val valueDescriptor = descriptor["value", KexArray(KexChar())]
        if (valueDescriptor == null) {
            callStack += DefaultConstructorCall(stringClass).wrap(name)
            return callStack
        }
        val value = fallback.generate(valueDescriptor, generationDepth + 1)

        val constructor = stringClass.getMethod("<init>", MethodDesc(arrayOf(types.getArrayType(types.charType)), types.voidType))
        callStack += ConstructorCall(stringClass, constructor, listOf(value)).wrap(name)
        return callStack
    }
}