package org.jetbrains.research.kex.util

class UnreachableException(message: String) : Exception(message)

fun unreachable(message: String): Nothing = unreachable { message }
inline fun unreachable(lazyMessage: () -> Any): Nothing = exit(lazyMessage)

fun exit(message: String): Nothing = exit { message }
inline fun exit(lazyMessage: () -> Any): Nothing = throw UnreachableException(lazyMessage().toString())
