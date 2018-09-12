@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

package org.jetbrains.research.kex.test

class ArrayTests {
    fun testArrayRead() {
        val simpleArray = intArrayOf(0, 1, 2, 3, 4)
        val length = simpleArray.size

        if (length != 5) {
            Intrinsics.assertUnreachable()
        }
        Intrinsics.assertReachable(length == 5)

        for (i in 0..4) {
            Intrinsics.assertReachable(simpleArray[i] == i)
        }
    }

    fun testArrayWrite() {
        val emptyArray = intArrayOf(0, 0, 0, 0, 0)

        for (i in 0 until 5) {
            emptyArray[i] = i * i
        }

        for (i in 0 until 5) {
            Intrinsics.assertReachable(emptyArray[i] == i * i)
        }
    }
}