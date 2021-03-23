package org.jetbrains.research.kex.test.generation

import org.jetbrains.research.kex.Intrinsics.kexAssert

class BasicGenerationTests {
    open class Point(val x: Int, val y: Int, val z: Int) {
        override fun toString() = "($x, $y, $z)"
    }
    class Point4(x: Int, y: Int, z: Int, val t: Int) : Point(x, y, z) {
        override fun toString() = "($x, $y, $z, $t)"
    }
    data class Line(val start: Point, val end: Point) {
        override fun toString() = "{$start, $end}"
    }
    data class DoublePoint(val x: Double, val y: Double, val z: Double) {
        override fun toString() = "($x, $y, $z)"
    }

    fun simplePointCheck(x1: Int, x2: Int) {
        val zero = Point(x = x1, y = 0, z = 1)
        val ten = Point(x = x2, y = 10, z = 10)

        if (ten.x > zero.x) {
            kexAssert()
        } else {
            kexAssert()
        }
        kexAssert()
    }

    fun pointCheck(p1: Point, p2: Point) {
        if (p1.x > p2.x) {
            kexAssert()
        } else if (p2 is Point4) {
            kexAssert()
        } else {
            kexAssert()
        }
    }

    fun testArray(array: Array<Point>) {
        if (array[0].x > 0) {
            kexAssert()
        }
        if (array[1].y < 0) {
            kexAssert()
        }
        kexAssert()
    }
}