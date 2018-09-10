package org.jetbrains.research.kex.util

class UnreachableException : Exception()

fun <T> unreachable(message: String): T = unreachable { message }
fun <T> unreachable(lazyMessage: () -> Any) = exit<T>(lazyMessage)

fun exit(message: String) = exit<Unit>(message)
fun exit(lazyMessage: () -> Any) = exit<Unit>(lazyMessage)

fun <T> exit(message: String): T = exit<T> { message }
fun <T> exit(lazyMessage: () -> Any): T {
    lazyMessage()
    require(false)
    throw UnreachableException()
}
