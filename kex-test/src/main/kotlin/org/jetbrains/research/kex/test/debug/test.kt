@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    fun test(a: IntArray) {
        if (a[0] == 2) {
            error("a")
        }
    }
}