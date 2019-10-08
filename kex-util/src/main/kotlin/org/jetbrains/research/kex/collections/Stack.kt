package org.jetbrains.research.kex.collections

import java.util.*

fun <T> stackOf(vararg elements: T) = Stack(elements.toList())

class Stack<T>(elements: Collection<T>) : AbstractCollection<T>() {
    private val inner = ArrayDeque(elements)

    override val size: Int
        get() = inner.size

    constructor() : this(listOf())

    fun push(element: T) = inner.push(element)
    fun pop(): T = inner.pop()
    fun peek(): T = inner.peek()

    fun popOrNull(): T? = if (isEmpty()) null else pop()

    override fun contains(element: T) = element in inner
    override fun containsAll(elements: Collection<T>) = inner.containsAll(elements)
    override fun isEmpty() = inner.isEmpty()
    override fun iterator() = inner.iterator()
}