package org.jetbrains.research.kex.annotations

internal class MutableAnnotatedParam(override val type: String) : AnnotatedParam {
    override val annotations: MutableList<AnnotationInfo> = mutableListOf()
    override fun equals(other: Any?) = other is AnnotatedParam && type == other.type
    override fun hashCode() = type.hashCode()
    override fun toString() = type
}
