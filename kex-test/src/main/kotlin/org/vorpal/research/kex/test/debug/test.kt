@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.vorpal.research.kex.test.debug

import org.vorpal.research.kex.intrinsics.AssertIntrinsics
//
//enum class TestEnum {
//    A, B, C
//}
class ObjectGenerationTests {
//    fun test(a: TestEnum) {
//        if (a != TestEnum.B) {
//            AssertIntrinsics.kexAssert(true)
//        } else {
//            AssertIntrinsics.kexAssert(true)
//        }
//    }


    fun testInt(a: Int) {
        if (a > 0) {
            AssertIntrinsics.kexAssert(true)
        } else {
            AssertIntrinsics.kexAssert(true)
        }
    }
}