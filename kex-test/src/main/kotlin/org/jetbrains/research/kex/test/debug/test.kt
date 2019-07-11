@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class ObjectTests {
    data class Point(val x: Int, val y: Int, val z: Int)
    data class Line(val start: Point, val end: Point)
    data class DoublePoint(val x: Double, val y: Double, val z: Double)
}