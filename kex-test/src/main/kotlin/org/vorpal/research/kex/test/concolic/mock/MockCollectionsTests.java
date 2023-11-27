package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;
import org.vorpal.research.kex.test.concolic.TestEnum;

import java.util.*;


@SuppressWarnings("ALL")
public class MockCollectionsTests {
    static class Container<T> {
        T elem;
    }

    static class NoMock {
        int field;

        int foo() {
            return field;
        }
    }

    /*
        public void TestNoMock(Container<NoMock> container) {
            if (container.elem.foo() == 42) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        }
    */
    public void TestMockContainer(Container<ToMock> container) {
        if (container.elem.foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }
/*
    public void testMockList(ArrayList<ToMock> list) {
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

    public void testMockSet(HashSet<ToMock> set) {
        Iterator<ToMock> iterator = set.iterator();
        if (iterator.next().foo() == 35) {
            if (iterator.next().foo() == -1) {
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
