@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    fun test(a: IntArray) {
        for (i in 0..a.size) {
            if (a[i] > 0) {
                error("a")
            }
        }
    }
}