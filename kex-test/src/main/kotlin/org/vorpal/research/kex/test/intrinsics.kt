package org.vorpal.research.kex.test

import org.vorpal.research.kex.intrinsics.AssertIntrinsics
import org.vorpal.research.kex.intrinsics.CollectionIntrinsics

class IntrinsicsTest {
    fun testContains(array: ByteArray) {
        if (CollectionIntrinsics.containsByte(array, 12)) {
            AssertIntrinsics.kexAssert(true)
        }
    }
}