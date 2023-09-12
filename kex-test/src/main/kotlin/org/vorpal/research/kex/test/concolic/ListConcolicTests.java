package org.vorpal.research.kex.test.concolic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

@SuppressWarnings("ALL")
public class ListConcolicTests {

    int innerValue = 10;

    /*public int someFunction(Point point) {
        point.y = point.x;
        point.x = innerValue;
        innerValue += 1;
        if (point.x == 20) throw new IllegalStateException();
        else return point.x;
    }*/

    public void secondTestFunction() {
        int a = 10;
        int b = innerValue;
        assert(a==b);
    }

    public static int testArrayList(ArrayList<Point> points) {
        points.get(0).x = 5;
        points.get(1).y++;
        if (points.get(1).y != 10) return 19;
        else throw new IllegalStateException();
    }

    public static void testLinkedList(LinkedList<Point> points) {
        if (points.size() == 0) return;
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

    public static void testArrayListIndexOf(ArrayList<Point> points, Point p) {
        if (points.size() != 1) {
            throw new IllegalStateException("c");
        }
        int i = points.indexOf(p);
        if (i >= 0) {
            throw new IllegalStateException("a");
        } else {
            throw new IllegalStateException("b");
        }
    }

    public static void testArrayListIndexOf2(ArrayList<Point> points, Point p) {
        int i = points.indexOf(p);
        if (i == -1) {
            throw new IllegalStateException("a");
        } else {
            throw new IllegalStateException("b");
        }
    }

    public static void testLinkedListIndexOf(LinkedList<Point> points, Point p) {
        if (points.size() != 1) {
            throw new IllegalStateException("c");
        }
        int i = points.indexOf(p);
        if (i >= 0) {
            throw new IllegalStateException("a");
        } else {
            throw new IllegalStateException("b");
        }
    }

    public static void testLinkedListIndexOf2(LinkedList<Point> points, Point p) {
        int i = points.indexOf(p);
        if (i == -1) {
            throw new IllegalStateException("a");
        } else {
            throw new IllegalStateException("b");
        }
    }
}
