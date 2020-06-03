@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    fun testLoop(a: Int, b: Int): Int {
        var x = a - b
        while (x < a) {
            if (b > x) {
                println("b bigger")
            }
            ++x
        }
        return x
    }
}