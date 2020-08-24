@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    class StringTest(val a: Int, val name: String)

    fun testAbstractClass(test: String) {
        if (test[0] == 'a') {
            println("a")
        }
    }

}