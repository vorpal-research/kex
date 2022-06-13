package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ExternalConstructorCall
import org.vorpal.research.kfg.type.SystemTypeNames

class ClassGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == KexClass(SystemTypeNames.classClass)

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val klassClass = cm.classClass
        val forNameMethod = klassClass.getMethod("forName", types.classType, types.stringType)

        val createForNameCall = { klassName: Descriptor ->
            actionSequence += ExternalConstructorCall(
                forNameMethod,
                listOf(
                    fallback.generate(
                        klassName,
                        generationDepth + 1
                    )
                )
            )
        }
        val randomKlassName = {
            convertToDescriptor(cm.concreteClasses.random(context.random).canonicalDesc)
        }

        val klassName = when (descriptor) {
            is ConstantDescriptor.Null -> randomKlassName()
            is ObjectDescriptor -> {
                descriptor["name", types.stringType.kexType] ?: randomKlassName()
            }
            else -> randomKlassName()
        }
        createForNameCall(klassName)
        return actionSequence
    }

}
