package org.jetbrains.research.kex.collections

fun <T> Collection<T>.firstOrDefault(default: T): T = firstOrNull() ?: default
fun <T> Collection<T>.firstOrDefault(predicate: (T) -> Boolean, default: T): T = firstOrNull(predicate) ?: default

fun <T> Collection<T>.firstOrElse(action: () -> T): T = firstOrNull() ?: action()
fun <T> Collection<T>.firstOrElse(predicate: (T) -> Boolean, action: () -> T): T = firstOrNull(predicate) ?: action()

fun <T> Collection<T>.lastOrDefault(default: T): T = lastOrNull() ?: default
fun <T> Collection<T>.lastOrDefault(predicate: (T) -> Boolean, default: T): T = lastOrNull(predicate) ?: default

fun <T> Collection<T>.lastOrElse(action: () -> T): T = lastOrNull() ?: action()
fun <T> Collection<T>.lastOrElse(predicate: (T) -> Boolean, action: () -> T): T = lastOrNull(predicate) ?: action()
