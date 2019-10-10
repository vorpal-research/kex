package org.jetbrains.research.kex.util

class ListBuilder<T> {
    val list = mutableListOf<T>()

    operator fun T.unaryPlus() {
        list += this
    }

    operator fun Collection<T>.unaryPlus() {
        list += this
    }
}

fun <T> buildList(init: ListBuilder<T>.() -> Unit): List<T> {
    val builder = ListBuilder<T>()
    builder.init()
    return builder.list
}

fun <T> listOf(action: () -> T) = listOf(action())