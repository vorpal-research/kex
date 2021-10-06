package org.jetbrains.research.kex.test

import org.jetbrains.research.kex.intrinsics.AssertIntrinsics
import org.jetbrains.research.kex.intrinsics.CollectionIntrinsics

class IntrinsicsTest {
    fun testContains(array: ByteArray) {
        if (CollectionIntrinsics.containsByte(array, 12)) {
            AssertIntrinsics.kexAssert()
        }
    }
}