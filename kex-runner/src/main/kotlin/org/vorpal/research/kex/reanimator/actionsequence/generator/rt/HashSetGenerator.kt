package org.vorpal.research.kex.reanimator.actionsequence.generator.rt

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.ExternalMethodCall
import org.vorpal.research.kex.reanimator.actionsequence.generator.Generator
import org.vorpal.research.kex.reanimator.actionsequence.generator.GeneratorContext
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.util.getCtor
import org.vorpal.research.kfg.collectionClass
import org.vorpal.research.kfg.hashMapClass
import org.vorpal.research.kfg.hashSetClass
import org.vorpal.research.kfg.linkedHashSetClass
import org.vorpal.research.kfg.mapClass
import org.vorpal.research.kfg.setClass
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

class HashSetGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val hashSetClass = context.cm.hashSetClass
    private val linkedHashSetClass = context.cm.linkedHashSetClass
    private val hashMapClass = context.cm.hashMapClass
    private val kexHashMapClass = hashMapClass.kexType
    private val kexHashSetClass = hashSetClass.kexType
    private val kexLinkedHashSetClass = linkedHashSetClass.kexType

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == kexHashSetClass || descriptor.type == kexLinkedHashSetClass

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)

        when (val table = descriptor["map" to kexHashMapClass] as? ObjectDescriptor) {
            null -> {
                actionSequence += when (descriptor.type) {
                    kexHashSetClass -> DefaultConstructorCall(hashSetClass)
                    kexLinkedHashSetClass -> DefaultConstructorCall(linkedHashSetClass)
                    else -> unreachable { log.error("Unknown descriptor type: $descriptor") }
                }

            }
            else -> {
                val mapAS = fallback.generate(table, generationDepth)
                val keySet = ActionList(term { generate(context.cm.setClass.kexType) }.name)
                keySet += ExternalMethodCall(
                    context.cm.mapClass.getMethod("keySet", context.cm.setClass.asType),
                    mapAS,
                    emptyList()
                )

                actionSequence += when (descriptor.type) {
                    kexHashSetClass -> ConstructorCall(
                        hashSetClass.getCtor(context.cm.collectionClass.asType),
                        listOf(keySet)
                    )

                    kexLinkedHashSetClass -> ConstructorCall(
                        linkedHashSetClass.getCtor(context.cm.collectionClass.asType),
                        listOf(keySet)
                    )

                    else -> unreachable { log.error("Unknown descriptor type: $descriptor") }
                }
            }
        }

        actionSequence
    }
}
