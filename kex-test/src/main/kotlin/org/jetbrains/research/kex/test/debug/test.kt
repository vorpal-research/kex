@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import kotlin.math.sqrt

fun sqr(x: Double) = x * x

data class Point(val x: Double, val y: Double) {
    fun distance(other: Point): Double = sqrt(sqr(x - other.x) + sqr(y - other.y))
}

class Results(val elements: MutableMap<String, Point>) : MutableMap<String, Point> by elements {
    fun merge(other: Results): Results {
        val elements = this.elements.toMap().toMutableMap()
        elements.putAll(other.elements)
        return Results(elements)
    }
}