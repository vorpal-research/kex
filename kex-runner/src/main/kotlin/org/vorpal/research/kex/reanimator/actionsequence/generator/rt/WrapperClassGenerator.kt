package org.vorpal.research.kex.reanimator.actionsequence.generator.rt

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ConstructorCall
import org.vorpal.research.kex.reanimator.actionsequence.generator.Generator
import org.vorpal.research.kex.reanimator.actionsequence.generator.GeneratorContext
import org.vorpal.research.kex.util.getCtor
import org.vorpal.research.kex.util.getPrimitive
import org.vorpal.research.kfg.boolWrapper
import org.vorpal.research.kfg.byteWrapper
import org.vorpal.research.kfg.charWrapper
import org.vorpal.research.kfg.doubleWrapper
import org.vorpal.research.kfg.floatWrapper
import org.vorpal.research.kfg.intWrapper
import org.vorpal.research.kfg.longWrapper
import org.vorpal.research.kfg.shortWrapper
import org.vorpal.research.kfg.type.ClassType

class WrapperClassGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context
    private val supportedTypes = setOf(
        context.cm.boolWrapper.kexType,
        context.cm.byteWrapper.kexType,
        context.cm.charWrapper.kexType,
        context.cm.shortWrapper.kexType,
        context.cm.intWrapper.kexType,
        context.cm.longWrapper.kexType,
        context.cm.floatWrapper.kexType,
        context.cm.doubleWrapper.kexType
    )

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type in supportedTypes && descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val descriptorKfgType = descriptor.type.getKfgType(types) as ClassType
        val baseKfgType = types.getPrimitive(descriptorKfgType)
        val baseType = baseKfgType.kexType

        val valueDesc = when {
            "value" to baseType in descriptor.fields -> descriptor["value" to baseType]!!
            else -> org.vorpal.research.kex.descriptor.descriptor { default(baseType) }
        }
        val constructor = descriptorKfgType.klass.getCtor(baseKfgType)
        val valueAS = fallback.generate(valueDesc, generationDepth)
        actionSequence += ConstructorCall(constructor, listOf(valueAS))

        actionSequence
    }
}
