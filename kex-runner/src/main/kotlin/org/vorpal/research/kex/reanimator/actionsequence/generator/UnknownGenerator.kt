package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.ktype.KexRtManager.rtUnmapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.*
import org.vorpal.research.kfg.UnknownInstanceException
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.logging.log

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

                actionSequence += ReflectionNewInstance(kfgClass.asType)
                for ((field, value) in descriptor.fields) {
                    val fieldType = field.second.getKfgType(types).rtUnmapped
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
                    if (element neq kfgArray.component.kexType.defaultDescriptor) {
                        val indexAS = PrimaryValue(index)
                        val elementAS = fallback.generate(element)
                        actionSequence += ReflectionArrayWrite(element.type.getKfgType(types), indexAS, elementAS)
                    }
                }
            }

            is ClassDescriptor -> {
                val kfgClass = (descriptor.type.getKfgType(types) as ClassType).klass
                for ((field, value) in descriptor.fields) {
                    val fieldType = field.second.getKfgType(types).rtUnmapped
                    val kfgField = try {
                        kfgClass.getField(field.first, fieldType)
                    } catch (e: UnknownInstanceException) {
                        log.warn("Field ${field.first}: ${field.second} is not found in class $kfgClass")
                        continue
                    }
                    val valueAS = fallback.generate(value)
                    actionSequence += ReflectionSetStaticField(kfgField, valueAS)
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

    private val Descriptor.wrappedType
        get() = when (this.type) {
            is KexNull -> context.types.objectType
            else -> this.type.getKfgType(context.types)
        }
}
