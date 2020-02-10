package org.jetbrains.research.kex.util

inline fun <T> T.runIf(cond: Boolean, block: T.() -> Unit) {
    if (cond) block()
}

inline fun <T, R> T.runIf(cond: Boolean, default: R, block: T.() -> R): R {
    return if (cond) block() else default
}
