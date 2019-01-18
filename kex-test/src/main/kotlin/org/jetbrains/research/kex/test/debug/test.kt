@file:Suppress("SENSELESS_COMPARISON")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics

// add functions here to debug them

//fun testSimple(array: IntArray) {
//    var count = 0
//    if (array.size < 4) throw IllegalArgumentException()
//    if (array[0] == 1) ++count
//    if (array[1] == 2) ++count
//    if (array[2] == 3) ++count
//    if (array[3] == 4) ++count
//    if (count >= 3) {
//        println("Yes")
//        Intrinsics.assertReachable()
//    }
//}


//fun testUnreachableIf(x: Int): Int {
//    val set = "asdasdal;djadslas;d".length
//    val z = 10
//    val y = if (x > z && x < 0) {
//        Intrinsics.assertUnreachable()
//        println("lol")
//        142
//    } else {
//        Intrinsics.assertReachable(x <= z || x >= 0)
//        println("lol2")
//        x- 2 * x
//    }
//    Intrinsics.assertReachable()
//    return y
//}

fun testObjectArray(nullable: Array<Any?>, nonnulable: Array<Any>) {
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