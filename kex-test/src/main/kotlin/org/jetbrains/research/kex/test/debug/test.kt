@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug


class BasicTests {

    fun testUnknownArrayWrite(array: IntArray) {
        if (array.size < 5) {
            return
        }

        for (i in 0 until 5) {
            array[i] = i * i
        }
    }

}