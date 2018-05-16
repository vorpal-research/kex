package org.jetbrains.research.kex.util

fun <T> List<T>.contentEquals(other: List<T>): Boolean {
    if (this.size != other.size) return false
    return this.withIndex().fold(true) { acc, (index, value) -> acc && value == other[index] }
}