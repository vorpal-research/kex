@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.Intrinsics.kexAssert

class BasicTests {
    companion object {
        @JvmStatic
        private var point = Point(0, 0)

        @JvmStatic
        fun setMyPoint(p: Point) {
            point = p
        }
    }

    class Point(val x: Int, val y: Int)

//    fun test(a: ArrayList<Point>) {
//        if (a.size == 2) {
//            if (a[0].x == 10) {
//                if (a[1].y == 11) {
//                    error("a")
//                }
//            }
//        }
//    }
//
//    fun testStr(c: Char) {
//        if (c == "abcdef"[3]) {
//            error("a")
//        }
//    }

    fun test(test: Point?) {
        if (point.x == 0 && point.y == 1) {
            kexAssert(test != null)
        }
    }
}