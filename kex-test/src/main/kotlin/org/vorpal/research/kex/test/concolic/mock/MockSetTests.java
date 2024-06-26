package org.vorpal.research.kex.test.concolic.mock;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

import java.util.HashSet;
import java.util.Iterator;


// Results are unstable
@SuppressWarnings("ALL")
public class MockSetTests {
    public void testMockSetEasy(HashSet<ToMock> set) {
        Iterator<ToMock> iterator = set.iterator();
        if (!iterator.hasNext()) return;
        if (iterator.next().foo() == 42) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testMockSetMedium(HashSet<ToMock> set) {
        Iterator<ToMock> iterator = set.iterator();
        if (!iterator.hasNext()) return;
        HashSet<Integer> values = new HashSet<>();
        values.add(iterator.next().foo());
        if (values.contains(35)) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

//    public void testNoMockSetHard(HashSet<ToMock> set) {
//        Iterator<ToMock> iterator = set.iterator();
//        if (!iterator.hasNext()) return;
//
//        HashSet<Integer> values = new HashSet<>();
//        values.add(iterator.next().foo());
//
//        if (!iterator.hasNext()) return;
//        values.add(iterator.next().foo());
//        if (values.contains(35)) {
//            if (values.contains(22)) {
//                AssertIntrinsics.kexAssert(true);
//            } else {
//                AssertIntrinsics.kexAssert(true);
//
//            }
//        } else {
//            AssertIntrinsics.kexAssert(true);
//        }
//    }
}
