package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.descriptor.descriptor
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.StaticMethodCall
import org.jetbrains.research.kfg.type.SystemTypeNames

class ClassGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == KexClass(SystemTypeNames.classClass)

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        saveToCache(descriptor, callStack)

        val klassClass = cm.classClass
        val forNameMethod = klassClass.getMethod("forName", types.classType, types.stringType)

        val createForNameCall = { klassName: Descriptor ->
            callStack += StaticMethodCall(
                forNameMethod,
                listOf(
                    fallback.generate(
                        klassName,
                        generationDepth + 1
                    )
                )
            )
        }
        val randomKlassName = {
            cm.concreteClasses.random().canonicalDesc.descriptor
        }

        val klassName = when (descriptor) {
            is ConstantDescriptor.Null -> randomKlassName()
            is ObjectDescriptor -> {
                descriptor["name", types.stringType.kexType] ?: randomKlassName()
            }
            else -> randomKlassName()
        }
        createForNameCall(klassName)
        return callStack
    }

}