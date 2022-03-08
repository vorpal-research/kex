@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class ObjectGenerationTests {
    fun foo(list: ArrayList<Int>) {
        for (i in 0 until list.size) {
            if (i > 2 && list[i] == 7) {
                println("fuck")
            }
        }
    }
}