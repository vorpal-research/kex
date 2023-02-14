package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.reanimator.actionsequence.*
import org.vorpal.research.kfg.arrayListClass
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.objectType

class KexArrayListGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val kfgKexArrayList = context.cm.arrayListClass.rtMapped
    private val kexArrayList = kfgKexArrayList.kexType
    private val kfgObjectType = context.cm.type.objectType
    private val iteratorClass = KexClass("${kfgKexArrayList.fullName}\$Itr")
    private val listIteratorClass = KexClass("${kfgKexArrayList.fullName}\$ListItr")

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor is ObjectDescriptor
                && (descriptor.type == kexArrayList
                || descriptor.type == iteratorClass
                || descriptor.type == listIteratorClass)

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = when (descriptor.type) {
        kexArrayList -> generateList(descriptor as ObjectDescriptor, generationDepth)
        else -> generateIterator(descriptor as ObjectDescriptor, generationDepth)
    }

    private fun generateList(descriptor: ObjectDescriptor, generationDepth: Int): ActionSequence = with(context) {
        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val elementData = descriptor["elementData" to kfgObjectType.kexType.asArray()] as? ArrayDescriptor
        actionSequence += DefaultConstructorCall(kfgKexArrayList)

        if (elementData != null) {
            val addMethod = kfgKexArrayList.getMethod("add", cm.type.boolType, kfgObjectType)
            for (i in 0 until elementData.length) {
                val element = elementData[i] ?: descriptor { default(elementData.elementType) }
                val elementAS = fallback.generate(element, generationDepth)
                actionSequence += MethodCall(addMethod, listOf(elementAS))
            }
        }

        actionSequence
    }

    private fun generateIterator(descriptor: ObjectDescriptor, generationDepth: Int): ActionSequence = with(context) {
        val klass = (descriptor.type as KexClass).klass
        val kfgClass = context.cm[klass] as ConcreteClass

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val outerListField = kfgClass.fields.first { it.name.startsWith("this\$") && it.type == kfgClass.outerClass!!.type }
        val outerListClass = (outerListField.type as ClassType).klass
        val outerListFieldKey = outerListField.name to outerListField.type.kexType
        if (outerListFieldKey !in descriptor.fields) {
            descriptor[outerListFieldKey] = descriptor { `object`(kfgClass.outerClass!!.kexType) }
        }
        val outerListDescriptor = descriptor[outerListFieldKey]!!
        val outerListAS = fallback.generate(outerListDescriptor, generationDepth)

        val iteratorMethod = when (descriptor.type) {
            iteratorClass ->  outerListClass.getMethod("iterator", cm["java/util/Iterator"].type)
            else ->  outerListClass.getMethod("listIterator", cm["java/util/ListIterator"].type)
        }
        actionSequence += ExternalMethodCall(iteratorMethod, outerListAS, emptyList())

        val cursorValue = (descriptor["cursor" to KexInt] as? ConstantDescriptor.Int)?.value ?: 0
        repeat(cursorValue) {
            val nextMethod = kfgClass.getMethod("next", kfgObjectType)
            actionSequence += MethodCall(nextMethod, emptyList())
        }

        actionSequence
    }
}
