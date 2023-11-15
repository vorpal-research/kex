package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexJavaClass
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ExternalMethodCall
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.javaDesc
import org.vorpal.research.kfg.stringClass
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.stringType

class MethodGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    private val kfgMethodClass = context.cm["java/lang/reflect/Method"]
    private val kexMethodClass = kfgMethodClass.kexType
    private val kfgStringClass = context.cm.stringClass
    private val kfgJavaClass = KexJavaClass().kfgClass(context.types)
    private val classClass = KexClass(SystemTypeNames.classClass)
    private val stringClass = kfgStringClass.kexType

    override fun supports(descriptor: Descriptor): Boolean =
        descriptor.type == kexMethodClass && descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor

        val name = "${descriptor.term}".replace("[/$.]".toRegex(), "_")
        val actionSequence = ActionList(name)

        val klass = descriptor["class", classClass]
            ?: generateKlassByName(cm.concreteClasses.filter { it.methods.isNotEmpty() }
                .random(context.random).canonicalDesc)
        klass as ObjectDescriptor

        val klassName = klass["name", stringClass]!!.asStringValue!!
        val asmKlassName = klassName.asmString
        val kfgClass = cm[asmKlassName]

        val klassAS = fallback.generate(klass, generationDepth)
        var methodName = descriptor["name", types.stringType.kexType]?.asStringValue
        if (methodName == null || kfgClass.getMethods(methodName).isEmpty()) {
            methodName = kfgClass.methods.filterNot { it.isStaticInitializer }.random(
                context.random
            ).name
        }
        val methodNameDescriptor = fallback.generate(convertToDescriptor(methodName))

        val kfgMethod = kfgClass.getMethods(methodName).random(context.random)
        val argumentTypes = kfgMethod.argTypes.map { generateKlassByName(it.javaDesc) }
        val arrayDescriptor = descriptor {
            val array = array(argumentTypes.size, classClass)
            for ((index, element) in argumentTypes.withIndex()) {
                array[index] = element
            }
            array
        }
        val typesAS = fallback.generate(arrayDescriptor)

        val getDeclMethod = kfgJavaClass.getMethod(
            "getDeclaredMethod",
            kfgMethodClass.asType,
            kfgStringClass.asType,
            kfgJavaClass.asType.asArray
        )

        actionSequence += ExternalMethodCall(getDeclMethod, klassAS, listOf(methodNameDescriptor, typesAS))

        val setAccessible = descriptor["override" to KexBool] ?: descriptor { const(false) }
        val setAccessibleAS = fallback.generate(setAccessible, generationDepth)
        val setAccessibleMethod = kfgMethodClass.getMethod("setAccessible", types.voidType, types.boolType)
        actionSequence += MethodCall(setAccessibleMethod, listOf(setAccessibleAS))

        actionSequence
    }

    private fun generateKlassByName(name: String): ObjectDescriptor {
        val nameDescriptor = convertToDescriptor(name)
        return descriptor {
            `object`(classClass).also { klass ->
                klass["name", stringClass] = nameDescriptor
            }
        } as ObjectDescriptor
    }
}
