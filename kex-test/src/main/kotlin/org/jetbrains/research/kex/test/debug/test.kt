@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.intrinsics.CollectionIntrinsics

class BasicTests {
    fun test(x: String) {
        val arr = CollectionIntrinsics.generateCharArray(10) { 'a' + it }
        val s = String(arr)
        if (s[2] == 'c') {
            error("")
        }
    }
}