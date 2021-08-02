@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE", "SENSELESS_COMPARISON")

package org.jetbrains.research.kex.test

import org.jetbrains.research.kex.intrinsics.AssertIntrinsics.kexAssert
import org.jetbrains.research.kex.intrinsics.AssertIntrinsics.kexUnreachable

open class ArrayTests {
    fun testArrayRead() {
        val simpleArray = intArrayOf(0, 1, 2, 3, 4)
        val length = simpleArray.size

        if (length != 5) {
            kexUnreachable()
        }
        kexAssert(length == 5)

        var i = 0
        while (i < 5) {
            kexAssert(simpleArray[i] == i)
            ++i
        }
        kexAssert()
    }

    fun testArrayWrite() {
        val emptyArray = intArrayOf(0, 1, 2, 3, 0)

        var i = 0
        while (i < 5) {
            emptyArray[i] = i * i
            ++i
        }

        i = 0
        while (i < 5) {
            kexAssert(emptyArray[i] == i * i)
            ++i
        }
        kexAssert()
    }
}

open class ArrayLongTests {
    fun testUnknownArrayWrite(array: IntArray) {
        if (array.size < 5) {
            kexAssert(array.size < 5)
            return
        }
        kexAssert(array.size >= 5)

        var i = 0
        while (i < 5) {
            array[i] = i * i
            ++i
        }

        i = 0
        while (i < 5) {
            if (array[i] == i * i) {
                kexAssert()
            }
            ++i
        }
        kexAssert()
    }

    fun testObjectArray(nullable: Array<Any?>, nonnulable: Array<Any>) {
        if (nullable.isNotEmpty()) {
            for (i in nullable) {
                if (i != null) kexAssert(i != null)
                else kexAssert(i == null)
            }
        }
        if (nonnulable.isNotEmpty()) {
            for (i in nonnulable) {
                kexAssert(i != null)
            }
            for (i in nonnulable) {
                if (i == null) kexUnreachable()
            }
        }
        kexAssert(nullable != null)
    }

    // open fun so it will not be inlined
    open fun getNonNullableArray(): Array<Any> = arrayOf(1, 2, 3, 4)

    fun testArrayReturn() {
        val array = getNonNullableArray()
        for (i in array) {
            kexAssert(i != null)
        }
        for (i in array) {
            if (i == null) kexUnreachable()
        }
    }
}