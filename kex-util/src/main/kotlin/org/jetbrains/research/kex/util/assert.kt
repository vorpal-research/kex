package org.jetbrains.research.kex.util

import kotlin.system.exitProcess

fun <T> unreachable(message: String): T = fail<T>(message)
fun <T> unreachable(lazyMessage: () -> Any) = fail<T>(lazyMessage)

fun exit(message: String) = exit<Unit>(message)
fun exit(lazyMessage: () -> Any) = exit<Unit>(lazyMessage)

fun <T> exit(message: String): T = exit<T> { message }
fun <T> exit(lazyMessage: () -> Any): T {
    lazyMessage()
    exitProcess(1)
}

fun <T> fail(message: String): T = error(message)
fun <T> fail(lazyMessage: () -> Any): T {
    lazyMessage()
    error("Failure")
}