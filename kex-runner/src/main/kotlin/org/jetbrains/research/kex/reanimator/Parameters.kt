package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kfg.ClassManager

data class Parameters<T>(
        val instance: T?,
        val arguments: List<T>,
        val statics: Set<T>
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

fun Parameters<Descriptor>.concreteParameters(cm: ClassManager) =
        Parameters(instance?.concretize(cm), arguments.map { it.concretize(cm) }, statics.map { it.concretize(cm) }.toSet())


