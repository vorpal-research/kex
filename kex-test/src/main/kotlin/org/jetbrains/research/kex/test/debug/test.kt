@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    data class Point(
        val x: Int,
        val y: Int
    )

    fun test(a: ArrayList<Point>): Int {
        val value = a[0].x
        if (value > 3) {
            return 1
        }
        return 0
    }
}