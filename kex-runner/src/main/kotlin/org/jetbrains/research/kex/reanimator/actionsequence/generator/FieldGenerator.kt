package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.descriptor.descriptor
import org.jetbrains.research.kex.ktype.KexJavaClass
import org.jetbrains.research.kex.ktype.KexString
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.actionsequence.ActionList
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.ExternalMethodCall
import org.jetbrains.research.kex.util.field
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.SystemTypeNames

class FieldGenerator(val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    val kfgJavaClass = KexJavaClass().kfgClass(context.types)
    val kfgFieldClass = context.cm[SystemTypeNames.field]
    val kexFieldClass = kfgFieldClass.kexType

    override fun supports(descriptor: Descriptor): Boolean = descriptor.type == kexFieldClass

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as ObjectDescriptor
        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)

        var klass: Class? = null

        val klassDescriptor = descriptor["clazz" to KexJavaClass()]?.also { klassDesc ->
            klassDesc as ObjectDescriptor
            val klassName = klassDesc["name" to KexString()]?.asStringValue
            klass = klassName?.let { cm[it] } ?: context.cm.concreteClasses.random().also {
                klassDesc["name" to KexString()] = descriptor { string(it.canonicalDesc) }
            }
        } ?: descriptor {
            val klassDesc = `object`(KexJavaClass())
            klass = context.cm.concreteClasses.random()
            klassDesc["name" to KexString()] = string(klass!!.canonicalDesc)
            klassDesc
        }

        val generatedKlass = fallback.generate(klassDescriptor, generationDepth)
        val nameField = descriptor["name" to KexString()] ?: descriptor { string(klass!!.fields.random().name) }
        val generatedName = fallback.generate(nameField, generationDepth)

        val getDeclField = kfgJavaClass.getMethod("getDeclaredField", kfgFieldClass.type, types.stringType)

        actionSequence += ExternalMethodCall(getDeclField, generatedKlass, listOf(generatedName))
        actionSequence
    }
}