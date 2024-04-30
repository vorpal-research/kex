package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class MockPrimitivesTests {
    interface PrimitiveMocking {
        byte bt();

        boolean b();

        short s();

        int i();

        long l();

        char c();

        float f();

        double d();
    }

    static void ok() {
        AssertIntrinsics.kexAssert(true);
    }


    public void testPrimitives(PrimitiveMocking mock) {
        if (mock.b()) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }

        if (mock.bt() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }

        if (mock.s() == 50) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }

        if (mock.i() == 60) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }

        if (mock.c() == 200) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }

        if (mock.l() == 1e5) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }

    }
}
