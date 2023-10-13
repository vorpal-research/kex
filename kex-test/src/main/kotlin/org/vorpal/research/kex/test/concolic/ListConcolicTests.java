package org.vorpal.research.kex.test.concolic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

@SuppressWarnings("ALL")
public class ListConcolicTests {

    int innerValue = 10;

    public String firstTestFunction(String x) {
        if (x.contains("\n")) throw new IllegalStateException();
        String y = x + "\n";
        if (y.contains("\t")) throw new IllegalStateException();
        return y;
    }

    /*public Integer secondTestFunction() {
        if (innerValue == -2) throw new IllegalStateException();
        return innerValue;
    }*/

    public static int testArrayList(ArrayList<Point> points) {
        points.get(0).x = 5;
        points.get(1).y++;
        if (points.get(1).y != 10) return points.get(0).x;
        else return points.get(0).y;
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
