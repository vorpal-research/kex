package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.state.transformer.getCtor

class KexLinkedListGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val kfgKexLinkedList = context.cm.linkedListClass.rtMapped
    private val kexLinkedList = kfgKexLinkedList.kexType

    override fun supports(descriptor: Descriptor): Boolean = descriptor.type == kexLinkedList

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with (context) {
        descriptor as ObjectDescriptor
        val kexArrayListType = context.types.arrayListType.kexType.rtMapped
        val inner = descriptor["inner" to kexArrayListType]

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        actionSequence += when (inner) {
            null -> DefaultConstructorCall(kfgKexLinkedList)
            else -> {
                val innerAS = fallback.generate(inner, generationDepth)
                val collectionCtor = kfgKexLinkedList.getCtor(context.types.collectionType)
                ConstructorCall(collectionCtor, listOf(innerAS))
            }
        }

        actionSequence
    }
}