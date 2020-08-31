@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    class Value<T>(val name: T)

//    class LocalArray() {
//        val set = hashMapOf<Int, Int>()
//    }

    fun testAbstractClass(array: Value<Boolean>) {
        if (array.name == true) {
            println("a")
        }
    }

}