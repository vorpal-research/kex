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
        } else if (mx > my) {
            System.out.println("fail");
            res = my - mx;
        } else {
            throw new IllegalArgumentException();
        }
        return res;
    }

//    public int testWrapper(ArrayList<Integer> b) {
//        if (b.get(0) < 0) {
//            throw new IllegalArgumentException();
//        }
//        return b.size();
//    }

    public boolean testFoo(boolean b) {
        if (b) {
            foo(new Point(100, 100), new Point(0, 0));
            return false;
        } else {
            try {
                foo(new Point(0, 0), new Point(0, 0));
            } catch (IllegalArgumentException e) {
                System.err.println("asdsad");
            }
            return true;
        }
    }
}

