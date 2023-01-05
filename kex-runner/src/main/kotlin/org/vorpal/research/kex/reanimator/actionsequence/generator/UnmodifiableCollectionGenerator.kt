package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ExternalConstructorCall
import org.vorpal.research.kex.util.dekapitalize
import org.vorpal.research.kfg.collectionClass
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.listType

class UnmodifiableCollectionGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type.isSubtypeOf(
            context.types,
            KexClass(SystemTypeNames.unmodifiableCollection)
        ) && descriptor is ObjectDescriptor

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
