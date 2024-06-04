package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

import java.util.HashSet;
import java.util.Iterator;

@SuppressWarnings("ALL")
public class SetConcolicTests {
    public static void testCharHashSetIterator(HashSet<Character> chars) {
        if (chars.contains(null)) {
            throw new IllegalStateException();
        }
//        if (chars.size() > 1) {
//            throw new IllegalStateException();
//        }
        Iterator<Character> it = chars.iterator();
        if (it.hasNext()) {
            if (it.next().charValue() == 'v') {
                throw new IllegalStateException();
            } else {
                throw new IllegalStateException();
            }
        } else {
            throw new IllegalStateException();
        }
    }

    public static void testHashSetContains(HashSet<Point> points, Point p) {
        AssertIntrinsics.kexNotNull(p);
        if (points.contains(p)) {
            throw new IllegalStateException();
        } else {
            throw new IllegalStateException();
        }
    }
}
