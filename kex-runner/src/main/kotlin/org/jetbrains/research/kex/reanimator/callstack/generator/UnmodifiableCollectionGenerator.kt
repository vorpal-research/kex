package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.descriptor.descriptor
import org.jetbrains.research.kex.descriptor.unmodifiableCollection
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.ExternalConstructorCall
import org.jetbrains.research.kex.util.dekapitalize
import org.jetbrains.research.kfg.type.SystemTypeNames

class UnmodifiableCollectionGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type.isSubtypeOf(context.types, KexClass(SystemTypeNames.unmodifiableCollection))

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as ObjectDescriptor
        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        saveToCache(descriptor, callStack)

        val kfgCollectionType = cm.collectionClass
        val kexCollectionType = cm.collectionClass.kexType
        val innerCollection = descriptor["c" to kexCollectionType] ?: descriptor { `object`(kexCollectionType) }

        val innerCS = fallback.generate(
            innerCollection,
            generationDepth + 1
        )

        val descType = descriptor.type.name.takeLastWhile { it != '$' }
        callStack += ExternalConstructorCall(
            kfgCollectionType.getMethod(descType.dekapitalize(), types.listType), listOf(innerCS)
        )

        return callStack
    }
}