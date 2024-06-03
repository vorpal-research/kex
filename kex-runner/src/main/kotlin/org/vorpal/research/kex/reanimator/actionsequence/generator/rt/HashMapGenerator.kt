package org.vorpal.research.kex.reanimator.actionsequence.generator.rt

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kex.reanimator.actionsequence.generator.Generator
import org.vorpal.research.kex.reanimator.actionsequence.generator.GeneratorContext
import org.vorpal.research.kfg.hashMapClass
import org.vorpal.research.kfg.linkedHashMapClass
import org.vorpal.research.kfg.objectClass
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

class HashMapGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val objectClass = context.cm.objectClass
    private val hashMapClass = context.cm.hashMapClass
    private val linkedHashMapClass = context.cm.linkedHashMapClass
    private val kexObjectClass = objectClass.kexType
    private val kexHashMapClass = hashMapClass.kexType
    private val kexLinkedHashMapClass = linkedHashMapClass.kexType
    private val kexHashMapNode = context.cm[SystemTypeNames.hashMapClass + "\$Node"].kexType

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == kexHashMapClass || descriptor.type == kexLinkedHashMapClass

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)

        val table = descriptor["table" to kexHashMapNode] as? ArrayDescriptor
        actionSequence += when (descriptor.type) {
            kexHashMapClass -> DefaultConstructorCall(hashMapClass)
            kexLinkedHashMapClass -> DefaultConstructorCall(linkedHashMapClass)
            else -> unreachable { log.error("Unknown descriptor type: $descriptor") }
        }

        val addMethod = hashMapClass.getMethod("put", objectClass.asType, objectClass.asType, objectClass.asType)
        if (table != null) {
            for (i in 0 until table.length) {
                var element = table[i] as? ObjectDescriptor
                while (element != null) {
                    val key = element["key" to kexObjectClass]
                    val value = element["value" to kexObjectClass] ?: descriptor { `null` }
                    if (key == null || key is ConstantDescriptor.Null) continue

                    val keyAS = fallback.generate(key, generationDepth)
                    val valueAS = fallback.generate(value, generationDepth)
                    actionSequence += MethodCall(addMethod, listOf(keyAS, valueAS))

                    element = element["next" to kexHashMapNode] as? ObjectDescriptor
                }
            }
        }

        actionSequence
    }
}
