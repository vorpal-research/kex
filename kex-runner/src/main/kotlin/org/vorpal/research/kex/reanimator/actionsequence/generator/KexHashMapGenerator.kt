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
import org.vorpal.research.kfg.hashMapClass
import org.vorpal.research.kfg.linkedHashMapClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.arrayListType
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.assert.ktassert

class KexHashMapGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val kfgKexHashMap = context.cm.hashMapClass.rtMapped
    private val kfgKexLinkedHashMap = context.cm.linkedHashMapClass.rtMapped
    private val kexHashMap = kfgKexHashMap.kexType
    private val kexLinkedHashMap = kfgKexLinkedHashMap.kexType

    override fun supports(descriptor: Descriptor): Boolean =
        (descriptor.type == kexHashMap || descriptor.type == kexLinkedHashMap) && descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor
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
