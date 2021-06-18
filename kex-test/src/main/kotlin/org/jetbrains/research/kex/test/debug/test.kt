@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    class Point(
        val x: Int,
        val y: Double
    )

    fun test(a: Point, b: Point) {
        if (a.x > b.x) {
            if (a.y < b.y) {
                error("a")
            }
        }
    }
}