package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics

// add functions here to debug them

fun testSimple(array: IntArray) {
    var count = 0
    if (array.size < 4) throw IllegalArgumentException()
    if (array[0] == 1) ++count
    if (array[1] == 2) ++count
    if (array[2] == 3) ++count
    if (array[3] == 4) ++count
    if (count >= 3) {
        println("Yes")
        Intrinsics.assertReachable()
    }
}