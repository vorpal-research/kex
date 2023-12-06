package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class MockGenericsTests {
    static abstract class GenericMock<T> {
        abstract T foo();
    }


    void testGenericMock(GenericMock<Integer> mock) {
        if (mock.foo() == 42) {
            if (mock.foo() == 22) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    void testGenericHardMock(GenericMock<NoMock> mock) {
        NoMock aaa = new NoMock();
        aaa.field = 888;
        if (mock.foo().foo() == 42) {
            if (aaa.equals(mock.foo())) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    void testGenericRaw(GenericMock mock) {
        if (((NoMock) mock.foo()).foo() == 42) {
            if (((int) mock.foo()) == 22) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

}
