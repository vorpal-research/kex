package org.jetbrains.research.kex.util

inline fun <T : Any?> tryOrNull(action: () -> T?): T? = try {
    action()
} catch (e: Throwable) {
    null
}
