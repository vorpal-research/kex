package org.vorpal.research.kex.descriptor


fun Descriptor.transform(
    mapped: MutableMap<Descriptor, Descriptor>,
    transform: (Descriptor) -> Descriptor
): Descriptor {
    if (mapped[this] != null) return mapped[this]!!

    val newDescriptor = transform(this)
    mapped[this] = newDescriptor
    mapped[newDescriptor] = newDescriptor

    when (newDescriptor) {
        is ConstantDescriptor -> Unit
        is ClassDescriptor -> newDescriptor.fields.replaceAll { _, desc ->
            desc.transform(mapped, transform)
        }

        is ObjectDescriptor -> newDescriptor.fields.replaceAll { _, desc ->
            desc.transform(
                mapped,
                transform
            )
        }

        is MockDescriptor -> {
            newDescriptor.fields.replaceAll { _, desc -> desc.transform(mapped, transform) }
            newDescriptor.methodReturns.values.forEach { values ->
                values.replaceAll { d -> d.transform(mapped, transform) }
            }
        }

        is ArrayDescriptor -> newDescriptor.elements.replaceAll { _, u ->
            u.transform(
                mapped,
                transform
            )
        }
    }

    return newDescriptor
}

private fun Descriptor.mapImpl(
    mapped: MutableMap<Descriptor, Descriptor>,
    mappedToSelf: MutableSet<Descriptor>,
    transform: (Descriptor) -> Descriptor,
): Descriptor {
    if (mapped[this] != null) return mapped[this]!!
    if (this in mappedToSelf) return this
    TODO()
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

