package org.vorpal.research.kex.test.concolic;

import java.util.HashSet;
import java.util.Iterator;

public class SetConcolicTests {
    public static void testHashSetIterator(HashSet<Character> chars) {
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
        if (points.contains(p)) {
            throw new IllegalStateException();
        } else {
            throw new IllegalStateException();
        }
    }
}
