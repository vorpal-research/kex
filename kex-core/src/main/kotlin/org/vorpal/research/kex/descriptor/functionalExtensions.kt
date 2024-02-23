package org.vorpal.research.kex.descriptor


fun Descriptor.map(
    map: MutableMap<Descriptor, Descriptor>,
    mapper: (Descriptor) -> Descriptor
): Descriptor {
    if (map[this] != null) return map[this]!!

    val newDescriptor = mapper(this)
    map[this] = newDescriptor
    map[newDescriptor] = newDescriptor

    when (newDescriptor) {
        is ConstantDescriptor -> Unit
        is ClassDescriptor -> newDescriptor.fields.replaceAll { _, desc -> desc.map(map, mapper) }
        is ObjectDescriptor -> newDescriptor.fields.replaceAll { _, desc -> desc.map(map, mapper) }
        is MockDescriptor -> {
            newDescriptor.fields.replaceAll { _, desc -> desc.map(map, mapper) }
            newDescriptor.methodReturns.values.forEach { values ->
                values.replaceAll { d -> d.map(map, mapper) }
            }
        }

        is ArrayDescriptor -> newDescriptor.elements.replaceAll { _, u -> u.map(map, mapper) }
    }

    return newDescriptor
}


fun Descriptor.asSequence(): Sequence<Descriptor> = asSequence(mutableSetOf())
fun Descriptor.asSequence(visited: MutableSet<Descriptor>): Sequence<Descriptor> {
    if (this in visited) return emptySequence()

    return sequenceOf(this) + when (this) {
        is ConstantDescriptor -> emptySequence()
        is ClassDescriptor -> fields.values.asSequence().flatMap { it.asSequence(visited) }
        is ObjectDescriptor -> fields.values.asSequence().flatMap { it.asSequence(visited) }
        is MockDescriptor ->
            fields.values.asSequence().flatMap { it.asSequence(visited) } + allReturns

        is ArrayDescriptor -> elements.values.asSequence()
    }
}

fun Descriptor.forEach(
    visited: MutableSet<Descriptor> = mutableSetOf(),
    block: (Descriptor) -> Unit
) {
    this.asSequence(visited).forEach(block)
}

fun Descriptor.any(
    visited: MutableSet<Descriptor> = mutableSetOf(),
    predicate: (Descriptor) -> Boolean
): Boolean = asSequence(visited).any(predicate)

