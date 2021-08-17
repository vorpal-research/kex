@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.intrinsics.CollectionIntrinsics

class BasicTests {
    fun test(a: IntArray): Int {
        val c = CollectionIntrinsics.generateIntArray(5) { it }
        if (c[1] == 1) {
            return 1
        }
        return 0
    }
}