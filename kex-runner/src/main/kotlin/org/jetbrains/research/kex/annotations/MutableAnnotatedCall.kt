package org.jetbrains.research.kex.annotations

internal class MutableAnnotatedCall(private val packageNode: PackageTreeNode,
                                    override val name: String,
                                    override val type: CallType,
                                    override val returnType: String,
                                    override val params: MutableList<MutableAnnotatedParam>) : AnnotatedCall {
    override fun equals(other: Any?) = other is AnnotatedCall
            && name == other && type == other.type && params == other.params

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + params.hashCode()
        return result
    }

    override val className get() = packageNode.fullName

    override fun toString() = "$fullName(${params.joinToString()}): $returnType"

    override val annotations: MutableList<AnnotationInfo> = mutableListOf()
}
