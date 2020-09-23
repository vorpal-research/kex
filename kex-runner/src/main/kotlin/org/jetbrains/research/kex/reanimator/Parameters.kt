package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kfg.ClassManager

data class Parameters<T>(
        val instance: T?,
        val arguments: List<T>,
        val staticFields: Map<FieldTerm, T>
) {
    override fun toString(): String = buildString {
        appendLine("instance: $instance")
        if (arguments.isNotEmpty())
            appendLine("args: ${arguments.joinToString("\n")}")
        if (staticFields.isNotEmpty())
            appendLine("statics: ${staticFields.toList().joinToString("\n") { "${it.first} = {${it.second}}" }}")
    }
}

fun Parameters<Descriptor>.concreteParameters(cm: ClassManager) =
        Parameters(instance?.concretize(cm), arguments.map { it.concretize(cm) }, staticFields.mapValues { it.value.concretize(cm) })


