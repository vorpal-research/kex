package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.ExternalMethodCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kfg.collectionClass
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.setClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.arrayListType
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable

class KexHashMapGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val hashMapClassRtMapped = SystemTypeNames.hashMapClass.rtMapped
    private val linkedHashMapClassRtMapped = SystemTypeNames.linkedHashMap.rtMapped
    private val kfgCollectionClass = context.cm.collectionClass
    private val kfgSetClass = context.cm.setClass

    private val supportedMaps = setOf(
        KexClass(hashMapClassRtMapped),
        KexClass(linkedHashMapClassRtMapped)
    )

    private val supportedInnerSets = setOf(
        KexClass("${hashMapClassRtMapped}\$KeySet"),
        KexClass("${hashMapClassRtMapped}\$Values"),
        KexClass("${hashMapClassRtMapped}\$EntrySet"),
        KexClass("${linkedHashMapClassRtMapped}\$LinkedKeySet"),
        KexClass("${linkedHashMapClassRtMapped}\$LinkedValues"),
        KexClass("${linkedHashMapClassRtMapped}\$LinkedEntrySet"),
    )

    private val supportedIterators = setOf(
        KexClass("${hashMapClassRtMapped}\$KeyIterator"),
        KexClass("${hashMapClassRtMapped}\$ValueIterator"),
        KexClass("${hashMapClassRtMapped}\$EntryIterator"),
        KexClass("${linkedHashMapClassRtMapped}\$LinkedKeyIterator"),
        KexClass("${linkedHashMapClassRtMapped}\$LinkedValueIterator"),
        KexClass("${linkedHashMapClassRtMapped}\$LinkedEntryIterator"),
    )

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor is ObjectDescriptor
                && (descriptor.type in supportedMaps
                || descriptor.type in supportedInnerSets
                || descriptor.type in supportedIterators)

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = when (descriptor.type) {
        in supportedInnerSets -> generateInnerSet(descriptor as ObjectDescriptor, generationDepth)
        in supportedIterators -> generateIterator(descriptor as ObjectDescriptor, generationDepth)
        else -> generateHashMap(descriptor as ObjectDescriptor)
    }

    private fun generateIterator(descriptor: ObjectDescriptor, generationDepth: Int): ActionSequence = with(context) {
        val klass = (descriptor.type as KexClass).klass
        val kfgClass = context.cm[klass] as ConcreteClass

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val outerMapField =
            kfgClass.fields.first { it.name.startsWith("this\$") && it.type == kfgClass.outerClass!!.asType }
        val outerMapClass = (outerMapField.type as ClassType).klass
        val outerMapFieldKey = outerMapField.name to outerMapField.type.kexType
        if (outerMapFieldKey !in descriptor.fields) {
            descriptor[outerMapFieldKey] = descriptor { `object`(kfgClass.outerClass!!.kexType) }
        }
        val outerMapDescriptor = descriptor[outerMapFieldKey]!!
        val outerMapAS = fallback.generate(outerMapDescriptor, generationDepth)

        val outerSetAS = ActionList("${name}_OuterSet")
        val creationMethod = when {
            "KeyIterator" in kfgClass.fullName -> outerMapClass.getMethod("keySet", kfgSetClass.asType)
            "ValueIterator" in kfgClass.fullName -> outerMapClass.getMethod("values", kfgCollectionClass.asType)
            "EntryIterator" in kfgClass.fullName -> outerMapClass.getMethod("entrySet", kfgSetClass.asType)
            else -> unreachable("Unknown iterator impl: $kfgClass")
        }
        outerSetAS += ExternalMethodCall(creationMethod, outerMapAS, emptyList())

        val iteratorMethod = creationMethod.klass.getMethod("iterator", cm["java/util/Iterator"].asType)
        actionSequence += ExternalMethodCall(iteratorMethod, outerSetAS, emptyList())

        actionSequence
    }


    private fun generateInnerSet(descriptor: ObjectDescriptor, generationDepth: Int): ActionSequence = with(context) {
        val klass = (descriptor.type as KexClass).klass
        val kfgClass = context.cm[klass] as ConcreteClass

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val outerMapField =
            kfgClass.fields.first { it.name.startsWith("this\$") && it.type == kfgClass.outerClass!!.asType }
        val outerMapClass = (outerMapField.type as ClassType).klass
        val outerMapFieldKey = outerMapField.name to outerMapField.type.kexType
        if (outerMapFieldKey !in descriptor.fields) {
            descriptor[outerMapFieldKey] = descriptor { `object`(kfgClass.outerClass!!.kexType) }
        }
        val outerMapDescriptor = descriptor[outerMapFieldKey]!!
        val outerMapAS = fallback.generate(outerMapDescriptor, generationDepth)

        val createSetMethod = when {
            "KeySet" in kfgClass.fullName -> outerMapClass.getMethod("keySet", kfgSetClass.asType)
            "Values" in kfgClass.fullName -> outerMapClass.getMethod("values", kfgCollectionClass.asType)
            "EntrySet" in kfgClass.fullName -> outerMapClass.getMethod("entrySet", kfgSetClass.asType)
            else -> unreachable("Unexpected iterator type $kfgClass")
        }
        actionSequence += ExternalMethodCall(createSetMethod, outerMapAS, emptyList())

        actionSequence
    }

    private fun generateHashMap(descriptor: ObjectDescriptor): ActionSequence = with(context) {
        val kexArrayListType = context.types.arrayListType.kexType.rtMapped
        val keys = descriptor["keys" to kexArrayListType] as? ObjectDescriptor
        val values = descriptor["values" to kexArrayListType] as? ObjectDescriptor

        val name = "${descriptor.term}"
        val kfgClass = (descriptor.type.getKfgType(cm.type) as ClassType).klass
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        actionSequence += DefaultConstructorCall(kfgClass)
        if (keys != null && values != null) {
            val keyElementData = keys["elementData" to cm.type.objectType.kexType.asArray()] as? ArrayDescriptor
            val valueElementData = values["elementData" to cm.type.objectType.kexType.asArray()] as? ArrayDescriptor

            if (keyElementData != null && valueElementData != null) {
                ktassert(keyElementData.length == valueElementData.length)

                val putMethod = kfgClass.getMethod("put", cm.type.objectType, cm.type.objectType, cm.type.objectType)
                for (i in 0 until keyElementData.length) {
                    val keyAs = fallback.generate(keyElementData[i] ?: descriptor {
                        default(
                            keyElementData.elementType
                        )
                    })
                    val valueAs = fallback.generate(keyElementData[i] ?: descriptor {
                        default(
                            keyElementData.elementType
                        )
                    })
                    actionSequence += MethodCall(putMethod, listOf(keyAs, valueAs))
                }
            }
        }

        actionSequence
    }
}
