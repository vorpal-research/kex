package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.reanimator.actionsequence.*
import org.vorpal.research.kfg.arrayListClass
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.linkedListClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.objectType

class KexLinkedListGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val kfgKexLinkedList = context.cm.linkedListClass.rtMapped
    private val kexLinkedList = kfgKexLinkedList.kexType
    private val kfgKexArrayList = context.cm.arrayListClass.rtMapped
    private val kexArrayList = kfgKexArrayList.kexType
    private val kfgObjectType = context.cm.type.objectType
    private val listIteratorClass = KexClass("${kfgKexLinkedList.fullName}\$ListItr")

    override fun supports(descriptor: Descriptor): Boolean =
        (descriptor.type == kexLinkedList || descriptor.type == listIteratorClass) && descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = when (descriptor.type) {
        kexLinkedList -> generateList(descriptor as ObjectDescriptor, generationDepth)
        else -> generateIterator(descriptor as ObjectDescriptor, generationDepth)
    }

    private fun generateIterator(descriptor: ObjectDescriptor, generationDepth: Int): ActionSequence = with(context) {
        val klass = (descriptor.type as KexClass).klass
        val kfgClass = context.cm[klass] as ConcreteClass

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val outerListField =
            kfgClass.fields.first { it.name.startsWith("this\$") && it.type == kfgClass.outerClass!!.type }
        val outerListClass = (outerListField.type as ClassType).klass
        val outerListFieldKey = outerListField.name to outerListField.type.kexType
        if (outerListFieldKey !in descriptor.fields) {
            descriptor[outerListFieldKey] = descriptor { `object`(kfgClass.outerClass!!.kexType) }
        }
        val outerListDescriptor = descriptor[outerListFieldKey]!!
        val outerListAS = fallback.generate(outerListDescriptor, generationDepth)

        val iteratorMethod = outerListClass.getMethod("listIterator", cm["java/util/ListIterator"].type)
        actionSequence += ExternalMethodCall(iteratorMethod, outerListAS, emptyList())

        val cursorValue =
            ((descriptor["listItr" to KexClass("${kfgKexArrayList.fullName}\$ListItr")] as? ObjectDescriptor)
                ?.get("cursor", KexInt) as? ConstantDescriptor.Int
                    )?.value ?: 0
        repeat(cursorValue) {
            val nextMethod = kfgClass.getMethod("next", kfgObjectType)
            actionSequence += MethodCall(nextMethod, emptyList())
        }

        actionSequence
    }

    private fun generateList(descriptor: ObjectDescriptor, generationDepth: Int): ActionSequence = with(context) {
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
