package org.jetbrains.research.kex.util

fun defaultHashCode(vararg objects: Any): Int {
    var result = 1
    for (`object` in objects) {
        result = 31 * result + `object`.hashCode()
    }
    return result
}