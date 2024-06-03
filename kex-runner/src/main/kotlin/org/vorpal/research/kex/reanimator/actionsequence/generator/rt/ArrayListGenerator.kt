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
import org.vorpal.research.kfg.arrayListClass
import org.vorpal.research.kfg.type.objectType

class ArrayListGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    private val kfgArrayList = context.cm.arrayListClass
    private val kexArrayList = kfgArrayList.kexType
    private val kfgObjectType = context.cm.type.objectType

    override fun supports(descriptor: Descriptor): Boolean = descriptor.type == kexArrayList

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val elementData = descriptor["elementData" to kfgObjectType.kexType.asArray()] as? ArrayDescriptor
        val size = (descriptor["size" to KexInt] as? ConstantDescriptor.Int)?.value ?: elementData?.length ?: 0
        actionSequence += DefaultConstructorCall(kfgArrayList)

        val addMethod = kfgArrayList.getMethod("add", cm.type.boolType, kfgObjectType)
        if (elementData != null) {
            for (i in 0 until size) {
                val element = elementData[i] ?: descriptor { default(elementData.elementType) }
                val elementAS = fallback.generate(element, generationDepth)
                actionSequence += MethodCall(addMethod, listOf(elementAS))
            }
        } else if (size > 0) {
            for (i in 0 until size) {
                val elementAS = fallback.generate(descriptor { `null` }, generationDepth)
                actionSequence += MethodCall(addMethod, listOf(elementAS))
            }
        }

        actionSequence
    }
}
