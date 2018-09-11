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
        val size = emptyArray.size
        for (i in 0 until size) {
            emptyArray[i] = 5 * 5
        }

//        Intrinsics.assertReachable()
//        if (emptyArray[0] == 10)
//            Intrinsics.assertReachable()
//        if (emptyArray[1] == 20)
//            Intrinsics.assertReachable()
//        if (emptyArray[2] == 30)
//            Intrinsics.assertReachable()
//        if (emptyArray[3] == 40)
//            Intrinsics.assertReachable()
//        if (emptyArray[4] == 50)
//            Intrinsics.assertReachable()
//        emptyArray[4] = 15
//        for (i in 0 until 1) {
//            val element = emptyArray[i]
//            if (element == 5 * 5)
//                Intrinsics.assertReachable()
//        }
//        Intrinsics.assertReachable(emptyArray[4] == 15)
    }
}