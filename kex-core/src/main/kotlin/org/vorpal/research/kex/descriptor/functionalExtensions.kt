package org.vorpal.research.kex.descriptor


fun <T : Descriptor> Descriptor.transform(
    mapped: MutableMap<Descriptor, T>,
    failureMappings: MutableSet<Descriptor> = mutableSetOf(),
    transformOrNull: (Descriptor) -> T?
): Descriptor {
    if (mapped[this] != null) return mapped[this]!!
    if (this in failureMappings) return this

    val newDescriptor = transformOrNull(this) ?: return this.also {
        failureMappings.add(this)
        this.transformChildren(mapped, failureMappings, transformOrNull)
    }

    mapped[this] = newDescriptor
    mapped[newDescriptor] = newDescriptor
    newDescriptor.transformChildren(mapped, failureMappings, transformOrNull)
    return newDescriptor
}

private fun <T : Descriptor> Descriptor.transformChildren(
    mapped: MutableMap<Descriptor, T>,
    failureMappings: MutableSet<Descriptor>,
    transformOrNull: (Descriptor) -> T?
) {
    when (this) {
        is ConstantDescriptor -> Unit
        is ClassDescriptor -> fields.replaceAll { _, descriptor ->
            descriptor.transform(mapped, failureMappings, transformOrNull)
        }

        is ObjectDescriptor -> fields.replaceAll { _, descriptor ->
            descriptor.transform(mapped, failureMappings, transformOrNull)
        }

        is MockDescriptor -> {
            fields.replaceAll { _, descriptor ->
                descriptor.transform(mapped, failureMappings, transformOrNull)
            }
            methodReturns.values.forEach { values ->
                values.replaceAll { descriptor ->
                    descriptor.transform(mapped, failureMappings, transformOrNull)
                }
            }
        }

        is ArrayDescriptor -> elements.replaceAll { _, descriptor ->
            descriptor.transform(mapped, failureMappings, transformOrNull)
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

