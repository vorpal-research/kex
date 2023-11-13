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

/*
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
*/
}
