@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.intrinsics.CollectionIntrinsics

class BasicTests {
    fun test(a: IntArray) {
        if (CollectionIntrinsics.containsInt(a, 3)) {
            error("a")
        }
    }
}