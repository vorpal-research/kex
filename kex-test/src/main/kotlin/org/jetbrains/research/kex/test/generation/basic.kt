package org.jetbrains.research.kex.test.generation

import org.jetbrains.research.kex.test.Intrinsics


class BasicGenerationTests {

    open class Point(val x: Int, val y: Int, val z: Int)
    class Point4(x: Int, y: Int, z: Int, val t: Int) : Point(x, y, z)
    data class Line(val start: Point, val end: Point)
    data class DoublePoint(val x: Double, val y: Double, val z: Double)

    fun simplePointCheck(x1: Int, x2: Int) {
        val zero = Point(x = x1, y = 0, z = 1)
        val ten = Point(x = x2, y = 10, z = 10)

        if (ten.x > zero.x) {
            Intrinsics.assertReachable()
        } else {
            Intrinsics.assertReachable()
        }
        Intrinsics.assertReachable()
    }

    fun pointCheck(p1: Point, p2: Point) {
        if (p1.x > p2.x) {
            Intrinsics.assertReachable()
        } else if (p2 is Point4) {
            Intrinsics.assertReachable()
        } else {
            Intrinsics.assertReachable()
        }
    }

    fun testArray(array: Array<Point>) {
        if (array[0].x > 0) {
            Intrinsics.assertReachable()
        }
        if (array[1].y < 0) {
            Intrinsics.assertReachable()
        }
        Intrinsics.assertReachable()
    }
}