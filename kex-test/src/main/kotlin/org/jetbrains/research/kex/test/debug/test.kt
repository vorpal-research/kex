@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics


class BasicTests {

    fun testUnknownArrayWrite(array: IntArray) {
        if (array.size < 5) {
            Intrinsics.assertReachable(array.size < 5)
            return
        }
        Intrinsics.assertReachable(array.size >= 5)

        for (i in 0 until 5) {
            array[i] = i * i
        }

        for (i in 0 until 5) {
            Intrinsics.assertReachable(array[i] == i * i)
        }
        Intrinsics.assertReachable()
    }

}