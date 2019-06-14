@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics


open class ArrayLongTests {
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

//    fun testObjectArray(nullable: Array<Any?>, nonnulable: Array<Any>) {
//        if (nullable.isNotEmpty()) {
//            for (i in nullable) {
//                if (i != null) Intrinsics.assertReachable(i != null)
//                else Intrinsics.assertReachable(i == null)
//            }
//        }
//        if (nonnulable.isNotEmpty()) {
//            for (i in nonnulable) {
//                Intrinsics.assertReachable(i != null)
//            }
//            for (i in nonnulable) {
//                if (i == null) Intrinsics.assertUnreachable()
//            }
//        }
//        Intrinsics.assertReachable(nullable != null)
//    }

    // open fun so it will not be inlined
    open fun getNonNullableArray(): Array<Any> = arrayOf(1, 2, 3, 4)

    fun testArrayReturn() {
        val array = getNonNullableArray()
        for (i in array) {
            Intrinsics.assertReachable(i != null)
        }
        for (i in array) {
            if (i == null) Intrinsics.assertUnreachable()
        }
    }
}