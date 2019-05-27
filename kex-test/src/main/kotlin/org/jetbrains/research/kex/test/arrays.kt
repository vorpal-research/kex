@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE", "SENSELESS_COMPARISON")

package org.jetbrains.research.kex.test

open class ArrayTests {
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
        Intrinsics.assertReachable()
    }

    fun testArrayWrite() {
        val emptyArray = intArrayOf(0, 1, 2, 3, 0)

        for (i in 0 until 5) {
            emptyArray[i] = i * i
        }

        for (i in 0 until 5) {
            Intrinsics.assertReachable(emptyArray[i] == i * i)
        }
        Intrinsics.assertReachable()
    }

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

    fun testObjectArray(nullable: Array<Any?>, nonnulable: Array<Any>) {
        if (nullable.isNotEmpty()) {
            for (i in nullable) {
                if (i != null) Intrinsics.assertReachable(i != null)
                else Intrinsics.assertReachable(i == null)
            }
        }
        if (nonnulable.isNotEmpty()) {
            for (i in nonnulable) {
                Intrinsics.assertReachable(i != null)
            }
            for (i in nonnulable) {
                if (i == null) Intrinsics.assertUnreachable()
            }
        }
        Intrinsics.assertReachable(nullable != null)
    }

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