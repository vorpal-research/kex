@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {


    fun testArrayWrite(array: IntArray) {
//        if (array.size < 3) return

        val l = 8
        for (i in 0 until 1) {
            array[i] = l
        }
    }

}