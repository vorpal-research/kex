package org.jetbrains.research.kex.annotations

internal class PackageTreeNode(val name: String,
                               private val parentPrivate: PackageTreeNode?) {
    val isRoot get() = parentPrivate == null
    val parent get() = parentPrivate!!

    val root: PackageTreeNode
        get() = run {
            var current = this
            while (!current.isRoot)
                current = current.parent
            current
        }

    val fullName: String = if (parentPrivate?.parentPrivate != null) "${parentPrivate.fullName}/$name" else name

    val nodes = mutableListOf<PackageTreeNode>()
    val entities = mutableListOf<MutableAnnotatedCall>()

    private fun buildString(builder: StringBuilder, offset: String) {
        builder.append(offset).append(name).append(":\n")
        val newOffset = "$offset    "
        for (entity in entities)
            builder.append(newOffset).append(entity.toString()).append('\n')
        for (node in nodes)
            node.buildString(builder, newOffset)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        buildString(builder, "")
        return builder.toString()
    }
}
