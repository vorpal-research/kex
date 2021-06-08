@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    fun test(list: ArrayList<Int>) {
        if (list[0] > 0) {
            println("a")
        }
    }
}