package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kfg.hashSetClass
import org.vorpal.research.kfg.linkedHashMapClass
import org.vorpal.research.kfg.linkedHashSetClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.arrayListType
import org.vorpal.research.kfg.type.objectType


class KexHashSetGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val kfgKexHashSet = context.cm.hashSetClass.rtMapped
    private val kfgKexLinkedHashSet = context.cm.linkedHashSetClass.rtMapped
    private val kexHashSet = kfgKexHashSet.kexType
    private val kexLinkedHashSet = kfgKexLinkedHashSet.kexType

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == kexHashSet || descriptor.type == kexLinkedHashSet

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor
        val kexLinkedHashMapType = context.cm.linkedHashMapClass.kexType.rtMapped
        val inner = descriptor["inner" to kexLinkedHashMapType] as? ObjectDescriptor

        val name = "${descriptor.term}"
        val kfgClass = (descriptor.type.getKfgType(cm.type) as ClassType).klass
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        actionSequence += DefaultConstructorCall(kfgClass)

        if (inner != null) {
            val kexArrayListType = context.types.arrayListType.kexType.rtMapped
            val keys = inner["keys" to kexArrayListType] as? ObjectDescriptor

            if (keys != null) {
                val keyElementData = keys["elementData" to cm.type.objectType.kexType.asArray()] as? ArrayDescriptor
                if (keyElementData != null) {
                    val addMethod =
                        kfgClass.getMethod("add", cm.type.boolType, cm.type.objectType)
                    for (i in 0 until keyElementData.length) {
                        val keyAs =
                            fallback.generate(keyElementData[i] ?: org.vorpal.research.kex.descriptor.descriptor {
                                default(
                                    keyElementData.elementType
                                )
                            })
                        actionSequence += MethodCall(addMethod, listOf(keyAs))
                    }
                }
            }
        }

        actionSequence
    }
}