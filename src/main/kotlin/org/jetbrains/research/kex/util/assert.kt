package org.jetbrains.research.kex.util

fun <T> unreachable(message: String): T = unreachable({ message })
inline fun <T> unreachable(lazyMessage: () -> Any) = exit<T>(lazyMessage)

fun exit(message: String) = exit<Unit>(message)
fun exit(lazyMessage: () -> Any) = exit<Unit>(lazyMessage)
fun <T> exit(message: String): T = exit<T>({ message })
inline fun <T> exit(lazyMessage: () -> Any): T {
    assert(false, lazyMessage)
    TODO()
}
