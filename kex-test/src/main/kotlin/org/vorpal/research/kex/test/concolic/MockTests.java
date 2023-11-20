package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class MockTests {
    public interface ToMock {
        int foo();

        String bar();

        ToMock recursion();
    }

/*
    public void testMockEasy(ToMock i) {
        if (i.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testMockTwo(ToMock i) {
        if (i.foo() == 42) {
            if (i.foo() == 25) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testMockMultipleEasy(ToMock first, ToMock second) {
        if (first.foo() == 42) {
            if (second.foo() == 25) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testMockMultipleHard(ToMock first, ToMock second) {
        if (first.foo() == 42) {
            if (second.foo() == 29) {
                if (first.foo() == second.foo() * 2) {
                    AssertIntrinsics.kexAssert(true);
                } else {
                    AssertIntrinsics.kexAssert(true);
                }
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

*/
/*
    public void testMockFooBarBoth(ToMock a) {
        if (a.foo() == 25) {
            if (a.bar() == "Not again...") {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            if (a.bar() == null) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        }
    }
*//*


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
*/

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

/*
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
            ok();
        } else {
            ok();
        }

        if (mock.i() == 60) {
            ok();
        } else {
            ok();
        }

        if (mock.c() == 200) {
            ok();
        } else {
            ok();
        }

        if (mock.l() == 1e5) {
            ok();
        } else {
            ok();
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
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                AssertIntrinsics.kexAssert(array[i][j].foo() == 10 * i + j);
            }
        }
    }
*/

}
