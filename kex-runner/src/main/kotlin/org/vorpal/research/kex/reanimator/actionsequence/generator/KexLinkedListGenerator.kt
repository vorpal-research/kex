package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kfg.arrayListClass
import org.vorpal.research.kfg.linkedListClass
import org.vorpal.research.kfg.type.objectType

class KexLinkedListGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val kfgKexLinkedList = context.cm.linkedListClass.rtMapped
    private val kexLinkedList = kfgKexLinkedList.kexType
    private val kfgKexArrayList = context.cm.arrayListClass.rtMapped
    private val kexArrayList = kfgKexArrayList.kexType
    private val kfgObjectType = context.cm.type.objectType

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == kexLinkedList && descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor
        val kexArrayListType = kexArrayList
        val inner = descriptor["inner" to kexArrayListType] as? ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)
        actionSequence += DefaultConstructorCall(kfgKexLinkedList)

        if (inner != null) {
            val elementData = inner["elementData" to kfgObjectType.kexType.asArray()] as? ArrayDescriptor

            if (elementData != null) {
                val addMethod = kfgKexLinkedList.getMethod("add", cm.type.boolType, kfgObjectType)
                for (i in 0 until elementData.length) {
                    val element = elementData[i] ?: descriptor { default(elementData.elementType) }
                    val elementAS = fallback.generate(element, generationDepth)
                    actionSequence += MethodCall(addMethod, listOf(elementAS))
                }
            }
        }

        actionSequence
    }
}
