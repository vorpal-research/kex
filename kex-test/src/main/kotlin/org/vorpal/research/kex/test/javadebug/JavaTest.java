package org.vorpal.research.kex.test.javadebug;

import org.vorpal.research.kex.test.concolic.Point;

@SuppressWarnings("ALL")
public class JavaTest {
    public int foo(Point a, Point b) {
        int ax = a.getX();
        int bx = b.getX();
        int mx = Math.max(ax, bx);

        int ay = a.getY();
        int by = b.getY();
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

