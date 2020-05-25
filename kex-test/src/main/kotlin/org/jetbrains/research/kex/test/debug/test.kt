@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    class Point (val x: Int)

    fun test(list: List<Point>) {
        if (list.size == 1) {
            println("a")
        }
    }
}