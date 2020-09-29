@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    
    class Intt(val a: Int)

    fun test(a: Array<Array<Intt>>) {
        if (a[0][0].a > 10) {
            println("a")
        }
    }

    fun test2(a: Array<IntArray>) {
        if (a[0][1] > 10) {
            println("b")
        }
    }

    fun test3(a: ArrayList<Int>) {
        if (a.size > 1) {
            println("a")
        }
    }

}