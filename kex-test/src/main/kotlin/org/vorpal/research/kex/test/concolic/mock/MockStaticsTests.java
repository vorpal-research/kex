package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class MockStaticsTests {
    static class WithStaticMock {
        static ToMock mock;
    }

    public void testMockStatic() {
        if (WithStaticMock.mock.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    static abstract class WithStaticInt {
        static int staticInt;

        public abstract int foo();
    }

    public void testMockHasStaticField() {
        if (WithStaticInt.staticInt == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testMockHasStaticField(WithStaticInt mock) {
        if (WithStaticInt.staticInt == 42) {
            if (mock.foo() == 11) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);

            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    static abstract class RecursionWithField {
        RecursionWithField stRec;

        abstract RecursionWithField fun();
    }

    public void testMockStaticRecursion(RecursionWithField argMock) {
        RecursionWithField a = argMock.fun();
        if (a.fun() == a.stRec) {
            if (a.stRec.fun() == a) {
                AssertIntrinsics.kexAssert(true);
            }
        }
    }

    static abstract class RecursionWithStaticField {

        static abstract class StaticWithMockField {
            static RecursionWithStaticField stRec;
        }

        abstract RecursionWithStaticField fun();
    }

    public void testMockStaticFieldRecursion(RecursionWithStaticField argMock) {
        RecursionWithStaticField a = argMock.fun();
        if (a.fun() == RecursionWithStaticField.StaticWithMockField.stRec) {
            if (RecursionWithStaticField.StaticWithMockField.stRec.fun() == a) {
                AssertIntrinsics.kexAssert(true);
            }
        }
    }
}
