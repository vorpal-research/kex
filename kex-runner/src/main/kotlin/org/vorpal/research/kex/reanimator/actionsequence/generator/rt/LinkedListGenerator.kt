package org.vorpal.research.kex.reanimator.actionsequence.generator.rt

import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kex.reanimator.actionsequence.generator.Generator
import org.vorpal.research.kex.reanimator.actionsequence.generator.GeneratorContext
import org.vorpal.research.kfg.linkedListClass
import org.vorpal.research.kfg.type.objectType

class LinkedListGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val kfgLinkedList = context.cm.linkedListClass
    private val kexLinkedList = kfgLinkedList.kexType
    private val kexLinkedListNode = context.cm["${kfgLinkedList.fullName}\$Node"].kexType
    private val kfgObjectType = context.cm.type.objectType

    override fun supports(descriptor: Descriptor): Boolean = descriptor.type == kexLinkedList

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        var node = descriptor["first" to kexLinkedListNode] as? ObjectDescriptor
        var size = (descriptor["size" to KexInt] as? ConstantDescriptor.Int)?.value ?: 0
        val addMethod = kfgLinkedList.getMethod("add", cm.type.boolType, kfgObjectType)
        actionSequence += DefaultConstructorCall(kfgLinkedList)

        while (node != null) {
            val element = node["item" to kfgObjectType.kexType] ?: descriptor { `null` }
            val elementAS = fallback.generate(element, generationDepth)
            actionSequence += MethodCall(addMethod, listOf(elementAS))

            node = node["next" to kexLinkedListNode] as? ObjectDescriptor
            --size
        }

        while (size > 0) {
            val elementAS = fallback.generate(descriptor { `null` }, generationDepth)
            actionSequence += MethodCall(addMethod, listOf(elementAS))
            --size
        }

        actionSequence
    }
}
