package org.jetbrains.research.kex.test.concolic;

import org.jetbrains.research.kex.intrinsics.AssertIntrinsics;

public class PrimitiveConcolicTests {
    public void test(TestEnum a) {
        if (a == TestEnum.B) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }


    public void testInt(int a) {
        if (a > 0) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }
}
