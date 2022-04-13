package org.jetbrains.research.kex.parameters

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.descriptor.ClassDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.Object2DescriptorConverter
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kfg.ClassManager

@Serializable
data class Parameters<T>(
    val instance: T?,
    val arguments: List<T>,
    val statics: Set<T> = setOf()
) {
    val asList get() = listOfNotNull(instance) + arguments + statics

    override fun toString(): String = buildString {
        appendLine("instance: $instance")
        if (arguments.isNotEmpty())
            appendLine("args: ${arguments.joinToString("\n")}")
        if (statics.isNotEmpty())
            appendLine("statics: ${statics.joinToString("\n")}")
    }
}

val Parameters<Any?>.asDescriptors: Parameters<Descriptor>
    get() {
        val context = Object2DescriptorConverter()
        return Parameters(
            context.convert(instance),
            arguments.map { context.convert(it) },
            statics.map { context.convert(it) }.toSet()
        )
    }

fun Parameters<Descriptor>.concreteParameters(cm: ClassManager) =
    Parameters(instance?.concretize(cm), arguments.map { it.concretize(cm) }, statics.map { it.concretize(cm) }.toSet())

fun Parameters<Descriptor>.filterStaticFinals(cm: ClassManager): Parameters<Descriptor> {
    val filteredStatics = statics.map { it.deepCopy() }.filterIsInstance<ClassDescriptor>().mapNotNull { klass ->
        val kfgClass = (klass.type as KexClass).kfgClass(cm.type)
        for ((name, type) in klass.fields.keys) {
            val field = kfgClass.getField(name, type.getKfgType(cm.type))
            if (field.isFinal) klass.remove(name to type)
        }
        when {
            klass.fields.isNotEmpty() -> klass
            else -> null
        }
    }.toSet()
    return Parameters(instance, arguments, filteredStatics)
}


