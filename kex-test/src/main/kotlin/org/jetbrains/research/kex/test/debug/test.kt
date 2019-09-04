@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import kotlin.math.atan
import kotlin.math.sqrt

fun sqr(x: Double) = x * x

data class Point(val x: Double, val y: Double) {
    fun distance(other: Point): Double = sqrt(sqr(x - other.x) + sqr(y - other.y))
}

data class Segment(val begin: Point, val end: Point) {
    override fun equals(other: Any?) =
            other is Segment && (begin == other.begin && end == other.end || end == other.begin && begin == other.end)

    override fun hashCode() =
            begin.hashCode() + end.hashCode()

    fun middle() = Point((begin.x + end.x) / 2, (begin.y + end.y) / 2)
    fun angle() = atan((end.y - begin.y) / (end.x - begin.x))
}