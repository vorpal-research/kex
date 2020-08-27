@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
//
//    class Value(val name: String)
//
//    class LocalArray() {
//        val set = hashMapOf<Int, Int>()
//    }
//
//    fun testAbstractClass(array: LocalArray) {
//        println("a")
//    }

    class Dbl(val value: Long) {
        override fun toString() = "Dbl(value = $value)"
    }

    fun test(d: Dbl) {
        if (d.value < 75L) {
            println(d)
        }
    }


}