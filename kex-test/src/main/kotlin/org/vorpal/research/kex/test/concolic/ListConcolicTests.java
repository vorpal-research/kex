package org.vorpal.research.kex.test.concolic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

@SuppressWarnings("ALL")
public class ListConcolicTests {

    int innerValue = 10;

    public Point firstTestFunction(Point point) {
        String x = "BBBBBB";
        Point a = new Point(7, 13);
        Point b = new Point(13, 7);
        if (point.x > point.y) a = b;
        else a = point;
        if (x != "bbb") b = a;
        else a = b;
        return b;
    }

    public String secondTestFunction() {
        String a = "AAAAAAAA";
        return a;
    }

    public Point thirdTestFunction(Point point) {
        Point b;
        if (point.x > innerValue) b = new Point(innerValue, point.x);
        else b = new Point(point.y, innerValue);
        Point c = new Point(b.y, b.x);
        return b;
    }

    public static int testArrayList(ArrayList<Point> points) {
        points.get(0).x = 5;
        points.get(1).y++;
        if (points.get(1).y != 10) return points.get(0).x;
        else return points.get(0).y;
    }

    public static Point testLinkedList(LinkedList<Point> points) {
        if (points.size() != 0) return points.get(0);
        return null;
        /*if (points.get(0).getX() == 10) {
            throw new IllegalStateException();
        }*/
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
