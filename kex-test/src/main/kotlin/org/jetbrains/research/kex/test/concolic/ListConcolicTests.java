package org.jetbrains.research.kex.test.concolic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

@SuppressWarnings("ALL")
public class ListConcolicTests {
    public static void testArrayList(ArrayList<Point> points) {
        if (points.get(0).getX() == 10) {
            throw new IllegalStateException();
        }
    }

    public static void testLinkedList(LinkedList<Point> points) {
        if (points.get(0).getX() == 10) {
            throw new IllegalStateException();
        }
    }

    public static void testArrayListIterator(ArrayList<Character> chars) {
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
}
