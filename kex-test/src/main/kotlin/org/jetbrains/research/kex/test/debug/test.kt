@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics

class BasicTests {
    open class Point(val x: Int, val y: Int, val z: Int) {
        override fun toString() = "($x, $y, $z)"
    }

    fun testArray(array: Array<Point>) {
        if (array[0].x > 3) {
            Intrinsics.assertReachable()
        }
        if (array[1].y < 0) {
            Intrinsics.assertReachable()
        }
        Intrinsics.assertReachable()
    }
}