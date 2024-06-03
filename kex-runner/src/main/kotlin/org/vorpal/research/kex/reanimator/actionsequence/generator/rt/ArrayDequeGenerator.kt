package org.vorpal.research.kex.reanimator.actionsequence.generator.rt

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kex.reanimator.actionsequence.generator.Generator
import org.vorpal.research.kex.reanimator.actionsequence.generator.GeneratorContext
import org.vorpal.research.kfg.arrayDequeClass
import org.vorpal.research.kfg.type.objectType

class ArrayDequeGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val kfgArrayDeque = context.cm.arrayDequeClass
    private val kexArrayDeque = kfgArrayDeque.kexType
    private val kfgObjectType = context.cm.type.objectType

    override fun supports(descriptor: Descriptor): Boolean = descriptor.type == kexArrayDeque

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val elements = descriptor["elements" to kfgObjectType.kexType.asArray()] as? ArrayDescriptor
        val head = (descriptor["head" to KexInt] as? ConstantDescriptor.Int)?.value ?: 0
        val tail = (descriptor["tail" to KexInt] as? ConstantDescriptor.Int)?.value ?: elements?.length ?: 0
        actionSequence += DefaultConstructorCall(kfgArrayDeque)

        val addMethod = kfgArrayDeque.getMethod("add", cm.type.boolType, kfgObjectType)
        if (elements != null) {
            for (i in head..<tail) {
                val element = elements[i] ?: descriptor { default(elements.elementType) }
                val elementAS = fallback.generate(element, generationDepth)
                actionSequence += MethodCall(addMethod, listOf(elementAS))
            }
        } else if (tail - head > 0) {
            for (i in head..<tail) {
                val elementAS = fallback.generate(descriptor { `null` }, generationDepth)
                actionSequence += MethodCall(addMethod, listOf(elementAS))
            }
        }

        actionSequence
    }
}
