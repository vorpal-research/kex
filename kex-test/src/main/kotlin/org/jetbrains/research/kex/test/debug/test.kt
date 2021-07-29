@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    fun test(a: String, b: String) {
        if (a.startsWith("32")) {
            if (b.contains(a)) {
                error("a")
            }
        }
    }
}