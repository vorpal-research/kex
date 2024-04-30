package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class MockReturnsMockTests {
    public void testMockReturnUnimplemented(ToMock a) {
        ToMock b = a.recursion();
        if (a == b) {
            if (b.foo() == a.foo()) {
                if (b.foo() == 42) {
                    AssertIntrinsics.kexAssert(true);
                } else {
                    AssertIntrinsics.kexAssert(true);
                }
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        }
    }

    public void testMockReturnUnimplementedCycle(ToMock a) {
        ToMock b = a.recursion();
        if (a != b) {
            ToMock c = b.recursion();
            if (c == a) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }


    public void testMockStringCycle(ToMock a) {
        ToMock b = a.recursion();
        if (a != b) {
            String abar = a.bar();
            String bbar = b.bar();
            if (abar != null && abar == bbar) {
                AssertIntrinsics.kexAssert(true);
            }
        }
    }

    /*
    // Unstable begins

    abstract class AbstractToMock {
        ToMock field;

        abstract int foo();
    }

    public void testAbstractToMock(AbstractToMock a) {
        ToMock b = a.field;
        if (b.foo() == 42) {
            if (a.foo() == 33) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

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


    interface MockWithEnum {
        TestEnum foo();
    }

    public void testMockAndEnum(MockWithEnum mock) {
        if (mock.foo() == TestEnum.B) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }


    public class Impl {
        ToMock field;
    }

    public void testMockIsField(Impl a) {
        ToMock mock = a.field;
        if (mock.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testArrayOfMocks(ToMock[][] array) {
        for (int i = 0; i < 1; i++) {
            for (int j = 0; j < 1; j++) {
                AssertIntrinsics.kexAssert(array[i][j].foo() == 10 * i + j);
            }
        }
    }

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

    public static abstract class WithStaticInt {
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

        static abstract class ContStatic {
            static RecursionWithStaticField stRec;
        }

        abstract RecursionWithStaticField fun();
    }

    public void testMockStaticFieldRecursion(RecursionWithStaticField argMock) {
        RecursionWithStaticField a = argMock.fun();
        if (a.fun() == RecursionWithStaticField.ContStatic.stRec) {
            if (RecursionWithStaticField.ContStatic.stRec.fun() == a) {
                AssertIntrinsics.kexAssert(true);
            }
        }
    }
*/
}
