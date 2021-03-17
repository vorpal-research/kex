package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.StaticFieldSetter
import org.jetbrains.research.kex.reanimator.callstack.UnknownCall
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.StaticFieldDescriptor

class StaticFieldGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor is StaticFieldDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as? StaticFieldDescriptor ?: throw IllegalArgumentException()
        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        descriptor.cache(callStack)
        val kfgClass = descriptor.klass.kfgClass(types)
        val kfgField = kfgClass.getField(descriptor.field, descriptor.type.getKfgType(types))
        if (visibilityLevel <= kfgField.visibility) {
            callStack += StaticFieldSetter(kfgClass, kfgField, generate(descriptor.value, generationDepth))
        } else {
            callStack += UnknownCall(descriptor.type.getKfgType(types), descriptor)
        }
        return descriptor.cached()!!
    }
}