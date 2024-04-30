package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

import java.util.*;


@SuppressWarnings("ALL")
public class MockListTests {
    static class Container<T> {
        T elem;
    }

    // Not a List, but indicates if generics brokes
    public void TestMockContainer(Container<ToMock> container) {
        if (container.elem.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testMockList1(ArrayList<ToMock> list) {
        if (list.get(0).foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testMockList2(ArrayList<ToMock> list) {
        if (list.get(0).foo() == 42) {
            if (list.get(0).foo() == 33) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    // coverage may be below 100%
    public void testMockList3(ArrayList<ToMock> list) {
        if (list.get(0).foo() == 42) {
            if (list.get(0).foo() == 33) {
                if (list.get(10).foo() == 25) {
                    AssertIntrinsics.kexAssert(true);
                }
            }
        }
    }
}
