package org.vorpal.research.kex.descriptor


fun <T : Descriptor> Descriptor.transform(
    mapping: MutableMap<Descriptor, T?>,
    transformOrNull: (Descriptor) -> T?
): Descriptor {
    if (this in mapping) return mapping[this] ?: this

    val newDescriptor = transformOrNull(this)
    mapping[this] = newDescriptor

    if (newDescriptor == null) {
        this.transformChildren(mapping, transformOrNull)
        return this
    }

    mapping[newDescriptor] = newDescriptor
    newDescriptor.transformChildren(mapping, transformOrNull)
    return newDescriptor
}

private fun <T : Descriptor> Descriptor.transformChildren(
    mapped: MutableMap<Descriptor, T?>,
    transformOrNull: (Descriptor) -> T?
) {
    when (this) {
        is ConstantDescriptor -> Unit
        is ClassDescriptor -> fields.replaceAll { _, descriptor ->
            descriptor.transform(mapped, transformOrNull)
        }

        is ObjectDescriptor -> fields.replaceAll { _, descriptor ->
            descriptor.transform(mapped, transformOrNull)
        }

        is MockDescriptor -> {
            fields.replaceAll { _, descriptor ->
                descriptor.transform(mapped, transformOrNull)
            }
            methodReturns.values.forEach { values ->
                values.replaceAll { descriptor ->
                    descriptor.transform(mapped, transformOrNull)
                }
            }
        }

        is ArrayDescriptor -> elements.replaceAll { _, descriptor ->
            descriptor.transform(mapped, transformOrNull)
        }
    }
}


fun Descriptor.asSequence(visited: MutableSet<Descriptor> = mutableSetOf()): Sequence<Descriptor> {
    if (!visited.add(this)) return emptySequence()

    return sequenceOf(this) + when (this) {
        is ConstantDescriptor -> emptySequence()
        is ClassDescriptor -> fields.values.asSequence().flatMap { it.asSequence(visited) }
        is ObjectDescriptor -> fields.values.asSequence().flatMap { it.asSequence(visited) }
        is MockDescriptor ->
            fields.values.asSequence().flatMap { it.asSequence(visited) } + allReturns

        is ArrayDescriptor -> elements.values.asSequence().flatMap { it.asSequence(visited) }
    }
}

fun Descriptor.forEach(
    visited: MutableSet<Descriptor> = mutableSetOf(),
    block: (Descriptor) -> Unit
) = asSequence(visited).forEach(block)

fun Descriptor.any(
    visited: MutableSet<Descriptor> = mutableSetOf(),
    predicate: (Descriptor) -> Boolean
): Boolean = asSequence(visited).any(predicate)

