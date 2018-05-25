package org.jetbrains.research.kex.util

inline fun <reified T : Any> Any.castTo() = this as? T ?: unreachable { "Cast failure" }