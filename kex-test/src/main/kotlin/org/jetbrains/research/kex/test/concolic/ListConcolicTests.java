package org.jetbrains.research.kex.test.concolic;

import java.util.ArrayList;
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
        for (Character character : chars) {
            if (character.charValue() == 'v') {
                throw new IllegalStateException();
            }
        }
    }
}
