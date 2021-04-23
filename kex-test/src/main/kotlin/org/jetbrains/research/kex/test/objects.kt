@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE", "SENSELESS_COMPARISON")
package org.jetbrains.research.kex.test

import org.jetbrains.research.kex.Intrinsics.kexAssert
import org.jetbrains.research.kex.Intrinsics.kexUnreachable

class ObjectTests {

    data class Point(val x: Int, val y: Int, val z: Int)
    data class Line(val start: Point, val end: Point)
    data class DoublePoint(val x: Double, val y: Double, val z: Double)

    fun simplePointCheck1(x1: Int, x2: Int) {
        val zero = Point(x = x1, y = 0, z = 0)
        val ten = Point(x = x2, y = 10, z = 10)

        if (ten.x > zero.x) {
            kexAssert()
        } else {
            kexAssert()
        }
    }

    fun simplePointCheck2(y1: Int, y2: Int) {
        val zero = Point(x = 0, y = y1, z = 0)
        val ten = Point(x = 10, y = y2, z = 10)

        if (ten.x > zero.x) {
            kexAssert()
        } else {
            kexUnreachable()
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
        kexAssert()
        return result
    }

    fun testNullability(line: Line) {
        val start = line.start
        if (null == start)
            kexUnreachable()
        else
            kexAssert()
    }
}
