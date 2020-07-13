package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.state.term.FieldTerm

data class Parameters<T>(
        val instance: T?,
        val arguments: List<T>,
        val staticFields: Map<FieldTerm, T>
) {
    override fun toString(): String = buildString {
        appendln("instance: $instance")
        if (arguments.isNotEmpty())
            appendln("args: ${arguments.joinToString("\n")}")
        if (staticFields.isNotEmpty())
            appendln("statics: ${staticFields.toList().joinToString("\n") { "${it.first} = {${it.second}}" }}")
    }
}
