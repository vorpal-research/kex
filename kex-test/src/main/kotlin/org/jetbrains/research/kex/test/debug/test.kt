@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    class Loop(val value: Int) {
        var prev: Loop? = null
//        var next: Loop? = null
    }

    fun test(p: Loop) {
        if (p.prev === p) {
            println("a")
        }
    }
}