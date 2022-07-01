package org.vorpal.research.kex.test.javadebug;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

import java.util.ArrayList;

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

    public static void testArrayListIndexOf(ArrayList<org.vorpal.research.kex.test.concolic.Point> points, org.vorpal.research.kex.test.concolic.Point p) {
        if (p == null) return;
        int i = points.indexOf(p);
        if (i > 0) {
            throw new IllegalStateException("a");
        }
        AssertIntrinsics.kexAssert(i == -1);
        throw new IllegalStateException("b");
    }

//    public static void testLinkedListIndexOf(LinkedList<org.vorpal.research.kex.test.concolic.Point> points, Point p) {
//        int i = points.indexOf(p);
//        if (i > 0) {
//            throw new IllegalStateException();
//        } else if (i == -1) {
//            throw new IllegalStateException();
//        }
//    }
}
