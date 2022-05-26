package org.vorpal.research.kex.test.javadebug;

import java.util.List;

public class JavaTest {
//    public static void testList(List<Point> points) {
//        if (points.get(0).x == 10) {
//            throw new IllegalStateException();
//        }
//    }

    public int foo(Point a, Point b) {
        int ax = a.x;
        int bx = b.x;
        int mx = Math.max(ax, bx);

        int ay = a.y;
        int by = b.y;
        int my = Math.min(ay, by);
        int res;
        if (mx < my) {
            System.out.println("success");
            res = mx - my;
        } else {
            System.out.println("fail");
            res = my - mx;
        }
        return res;
    }
}