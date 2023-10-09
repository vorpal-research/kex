package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ArrayClassConstantGetter
import org.vorpal.research.kex.reanimator.actionsequence.ClassConstantGetter
import org.vorpal.research.kex.reanimator.actionsequence.ExternalConstructorCall
import org.vorpal.research.kfg.classClass
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.classType
import org.vorpal.research.kfg.type.stringType

class ClassGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    private val primitives = context.cm.type.primitiveTypes.associateBy { it.name }

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == KexClass(SystemTypeNames.classClass)

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        val name = "${descriptor.term}".replace("[/$.]".toRegex(), "_")
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        val klassClass = cm.classClass
        val forNameMethod = klassClass.getMethod("forName", types.classType, types.stringType)

        val randomKlassName = {
            convertToDescriptor(cm.concreteClasses.random(context.random).canonicalDesc)
        }

        val klassName = when (descriptor) {
            is ConstantDescriptor.Null -> randomKlassName()
            is ObjectDescriptor -> {
                val nameDescriptor = descriptor["name", types.stringType.kexType]
                when {
                    nameDescriptor?.asStringValue.isNullOrBlank() -> randomKlassName()
                    else -> nameDescriptor!!
                }
            }
            else -> randomKlassName()
        }

        val stringName = klassName.asStringValue!!
        actionSequence += when {
            stringName in primitives -> ClassConstantGetter(primitives[stringName]!!)
            stringName.endsWith("[]") -> {
                val elementTypeDescriptor = org.vorpal.research.kex.descriptor.descriptor { klass(stringName.drop(2)) }
                val elementTypeAS = fallback.generate(elementTypeDescriptor)
                ArrayClassConstantGetter(elementTypeAS)
            }
            else -> ExternalConstructorCall(
                forNameMethod,
                listOf(

                    fallback.generate(
                        klassName,
                        generationDepth + 1
                    )
                )
            )
        }
        return actionSequence
    }

}
