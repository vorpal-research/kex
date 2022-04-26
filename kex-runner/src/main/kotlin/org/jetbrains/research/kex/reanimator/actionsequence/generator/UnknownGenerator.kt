package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.ArrayDescriptor
import org.jetbrains.research.kex.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.ktype.KexNull
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.actionsequence.*
import org.jetbrains.research.kfg.UnknownInstanceException
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kthelper.logging.log

class UnknownGenerator(
    val fallback: Generator
) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor): Boolean = true

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        val name = "${descriptor.term}"
        val actionSequence = ReflectionList(name)
        saveToCache(descriptor, actionSequence)

        when (descriptor) {
            is ConstantDescriptor -> fallback.generate(descriptor, generationDepth)
            is ObjectDescriptor -> {
                val kfgClass = (descriptor.type.getKfgType(types) as ClassType).klass

                actionSequence += ReflectionNewInstance(kfgClass.type)
                for ((field, value) in descriptor.fields) {
                    val fieldType = field.second.getKfgType(types)
                    val kfgField = try {
                        kfgClass.getField(field.first, fieldType)
                    } catch (e: UnknownInstanceException) {
                        log.warn("Field ${field.first}: ${field.second} is not found in class $kfgClass")
                        continue
                    }
                    val valueAS = fallback.generate(value)
                    actionSequence += ReflectionSetField(kfgField, valueAS)
                }
            }
            is ArrayDescriptor -> {
                val kfgArray = (descriptor.type.getKfgType(types) as ArrayType)
                val lengthCall = PrimaryValue(descriptor.length)

                actionSequence += ReflectionNewArray(kfgArray, lengthCall)
                for ((index, element) in descriptor.elements) {
                    val indexAS = PrimaryValue(index)
                    val elementAS = fallback.generate(element)
                    actionSequence += ReflectionArrayWrite(element.type.getKfgType(types), indexAS, elementAS)
                }
            }
            else -> UnknownSequence(
                "${descriptor.term}",
                descriptor.wrappedType,
                descriptor
            )
        }

        getFromCache(descriptor)!!
    }

    private val Descriptor.wrappedType get() = when {
        this.type is KexNull -> context.types.objectType
        else -> this.type.getKfgType(context.types)
    }
}