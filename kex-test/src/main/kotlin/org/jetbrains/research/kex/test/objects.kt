@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE", "SENSELESS_COMPARISON")
package org.jetbrains.research.kex.test

class ObjectTests {

    data class Point(val x: Int, val y: Int, val z: Int)
    data class Line(val start: Point, val end: Point)
    data class DoublePoint(val x: Double, val y: Double, val z: Double)

    fun simplePointCheck() {
        val zero = Point(x = 0, y = 0, z = 1)
        val ten = Point(x = 10, y = 10, z = 10)

        if (ten.x > zero.x) {
            Intrinsics.assertReachable()
        } else {
            // can't handle getters and setters yet
            Intrinsics.assertReachable()
        }
    }

    fun testObjects(a: Point, b: DoublePoint): Line {
        val xs = b.x - a.x
        val ys = b.y - a.y
        val zs = b.z - a.z

        val xe = a.x + b.x
        val ye = a.y + b.y
        val ze = a.z + b.z

        val result = Line(Point(xs.toInt(), ys.toInt(), zs.toInt()), Point(xe.toInt(), ye.toInt(), ze.toInt()))
        println(result.start)
        Intrinsics.assertReachable()
        return result
    }

    fun testNullability(line: Line) {
        val start = line.start
        if (null == start)
            Intrinsics.assertUnreachable()
        else
            Intrinsics.assertReachable()
    }
}
