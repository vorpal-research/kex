@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    
    class Intt(val a: Int)

    fun test(a: Array<Array<Intt>>) {
        if (a[0][0].a > 10) {
            println("a")
        }
    }

}