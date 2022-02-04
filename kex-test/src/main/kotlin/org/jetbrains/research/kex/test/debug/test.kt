@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.intrinsics.AssertIntrinsics

class ObjectGenerationTests {
    class Point(val x: Int, val y: Int) {
        override fun toString(): String {
            return "($x, $y)"
        }

    }

    class Line(var start: Point?, var end: Point?) {
        override fun toString(): String {
            return "{$start, $end}"
        }

    }

    fun testLine(line: Line?) {
        if (line == null) return
        if (line.start != null) {
            AssertIntrinsics.kexAssert()
        }
        if (line.end != null) {
            AssertIntrinsics.kexAssert()
        }
        if (line.start == line.end) {
            AssertIntrinsics.kexAssert()
        }
        AssertIntrinsics.kexAssert()
    }

    fun test2(a: Int) {
        var b = 0
        if (a > 10) b = 12313
        else b = -a

        var c = a - b
        var d = 0
        if (c > 0) d = 10
        else d = -10

        println(d)
    }
}