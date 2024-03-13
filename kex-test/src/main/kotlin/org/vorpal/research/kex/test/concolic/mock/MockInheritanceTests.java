package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;


@SuppressWarnings("ALL")
public class MockInheritanceTests {

    static abstract class Base {

        int foo(boolean b) {
            return 248;
        }
    }

    static abstract class Derived extends Base {

        @Override
        int foo(boolean b) {
            return 777;
        }
    }

    public static void testInheritance(Base a) {
        if (a.foo(false) == 222) {
            Derived d = (Derived) a;
            if (d.foo(true) == 888) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        }
    }
}
