package org.jetbrains.research.kex.test.concolic

import org.jetbrains.research.kex.intrinsics.AssertIntrinsics

enum class TestEnum {
    A, B, C
}

class PrimitiveConcolicTests {
    fun test(a: TestEnum) {
        if (a != TestEnum.B) {
            AssertIntrinsics.kexAssert(true)
        }
    }


    fun testInt(a: Int) {
        if (a > 0) {
            AssertIntrinsics.kexAssert(true)
        } else {
            AssertIntrinsics.kexAssert(true)
        }
    }
}