package org.vorpal.research.kex.test.javadebug;

import org.vorpal.research.kex.test.concolic.Point;

import java.util.ArrayList;
import java.util.LinkedList;

public class JavaTest {
//    public static void testList(List<Point> points) {
//        if (points.get(0).x == 10) {
//            throw new IllegalStateException();
//        }
//    }
//
//    public int foo(Point a, Point b) {
//        int ax = a.x;
//        int bx = b.x;
//        int mx = Math.max(ax, bx);
//
//        int ay = a.y;
//        int by = b.y;
//        int my = Math.min(ay, by);
//        int res;
//        if (mx < my) {
//            System.out.println("success");
//            res = mx - my;
//        } else {
//            System.out.println("fail");
//            res = my - mx;
//        }
//        return res;
//    }
//
//    public void foo2(char[][] a) {
//        if (a.length > 10) {
//            if (a[0].length < 2) {
//                System.out.println("s");
//            }
//        }
//    }
//    public void foo3(String[][] a) {
//        if (a.length > 10) {
//            if (a[0].length < 2) {
//                System.out.println("s");
//            }
//        }
//    }

//    public static void testArrayListIndexOf(ArrayList<org.vorpal.research.kex.test.concolic.Point> points, org.vorpal.research.kex.test.concolic.Point p) {
//        int i = points.indexOf(p);
//        if (i > 0) {
//            throw new IllegalStateException("a");
//        } else {
//            throw new IllegalStateException("b");
//        }
//    }

//    public static void testLinkedListIndexOf(LinkedList<Point> points, Point p) {
//        int i = points.indexOf(p);
//        if (i > 0) {
//            throw new IllegalStateException("a");
//        } else {
//            throw new IllegalStateException("b");
//        }
//    }
//
//    public static void testLinkedListIndexOf2(LinkedList<Point> points, Point p) {
//        int i = points.indexOf(p);
//        if (i == -1) {
//            throw new IllegalStateException("b");
//        } else {
//            throw new IllegalStateException("a");
//        }
//    }
//    public static void testArrayList(ArrayList<Point> points) {
//        if (points.size() == 0) return;
//        if (points.get(0).getX() == 10) {
//            throw new IllegalStateException();
//        }
//    }
}

