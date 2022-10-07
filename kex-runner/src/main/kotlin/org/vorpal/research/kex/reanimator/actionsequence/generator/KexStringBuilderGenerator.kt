package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.ktype.type
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.DefaultConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kfg.stringBufferClass
import org.vorpal.research.kfg.stringBuilderClass
import org.vorpal.research.kfg.type.ClassType

class KexStringBuilderGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val kfgKexStringBuilder = context.cm.stringBuilderClass.rtMapped
    private val kexStringBuilder = kfgKexStringBuilder.kexType
    private val kfgKexStringBuffer = context.cm.stringBufferClass.rtMapped
    private val kexStringBuffer = kfgKexStringBuffer.kexType

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == kexStringBuilder || descriptor.type == kexStringBuffer

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val kfgClass = (descriptor.type.getKfgType(cm.type) as ClassType).klass
        val value = descriptor["value" to KexChar().asArray()] as? ArrayDescriptor
        actionSequence += DefaultConstructorCall(kfgClass)

        if (value != null) {
            val appendMethod = kfgClass.getMethod(
                "append",
                kfgClass.type,
                cm.type.getArrayType(cm.type.charType)
            )
            val valueAS = fallback.generate(value, generationDepth)
            actionSequence += MethodCall(appendMethod, listOf(valueAS))
        }

        actionSequence
    }
}