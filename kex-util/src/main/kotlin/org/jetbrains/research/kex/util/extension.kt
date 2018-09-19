package org.jetbrains.research.kex.util

inline fun <T : Any?> Any.tryOrNull(action: () -> T?): T? = try {
    action()
} catch (e: Throwable) {
    null
}
