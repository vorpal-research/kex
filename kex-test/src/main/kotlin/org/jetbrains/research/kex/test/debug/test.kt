@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class ObjectGenerationTests {
    abstract class PP

    class Point(val x: Int, val y: Int) : PP() {
        override fun toString(): String {
            return "($x, $y)"
        }

    }
//
//    class Line(var start: Point?, var end: Point?) {
//        override fun toString(): String {
//            return "{$start, $end}"
//        }
//
//    }
//
//    fun testLine(line: Line?): Int {
//        if (line == null) return -1
//        if (line.start != null) {
//            AssertIntrinsics.kexAssert()
//        }
//        if (line.end != null) {
//            AssertIntrinsics.kexAssert()
//        }
//        if (line.start == line.end) {
//            AssertIntrinsics.kexAssert()
//        }
//        AssertIntrinsics.kexAssert()
//        return 0
//    }
//
//    fun test2(a: Int): Int {
//        var b = 0
//        if (a > 10) b = 12313
//        else b = -a
//
//        var c = a - b
//        var d = 0
//        if (c > 0) d = 10
//        else d = -10
//
//        println(d)
//        return d
//    }

    fun foo(a: PP) {
        val x = a.javaClass
        if (x == Point::class.java) {
            println("aa")
        }
    }
}