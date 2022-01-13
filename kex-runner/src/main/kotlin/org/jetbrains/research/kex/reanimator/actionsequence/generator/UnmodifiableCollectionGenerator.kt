package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.descriptor.descriptor
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.reanimator.actionsequence.ActionList
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.ExternalConstructorCall
import org.jetbrains.research.kex.util.dekapitalize
import org.jetbrains.research.kex.util.unmodifiableCollection
import org.jetbrains.research.kfg.type.SystemTypeNames

class UnmodifiableCollectionGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type.isSubtypeOf(context.types, KexClass(SystemTypeNames.unmodifiableCollection))

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor
        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val kfgCollectionType = cm.collectionClass
        val kexCollectionType = cm.collectionClass.kexType
        val innerCollection = descriptor["c" to kexCollectionType] ?: descriptor { `object`(kexCollectionType) }

        val innerCS = fallback.generate(
            innerCollection,
            generationDepth + 1
        )

        val descType = descriptor.type.name.takeLastWhile { it != '$' }
        actionSequence += ExternalConstructorCall(
            kfgCollectionType.getMethod(descType.dekapitalize(), types.listType), listOf(innerCS)
        )

        return actionSequence
    }
}