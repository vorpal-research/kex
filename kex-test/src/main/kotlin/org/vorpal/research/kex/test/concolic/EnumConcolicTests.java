package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

@SuppressWarnings("ALL")
public class EnumConcolicTests {
    public void testEnum(TestEnum a) {
        if (a == TestEnum.B) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testEnum2(TestEnum a) {
        if (a.name().equals("A")) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testEnum3(TestEnum a) {
        if (a.ordinal() == 0) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }
}
