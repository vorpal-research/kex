package org.vorpal.research.kex.test.concolic;

@SuppressWarnings("ALL")
public class AssertAndExceptionsTests1 {

    public void noRetConst(int a, int b) {
        if (a * b == 10) throw new IllegalStateException();
        int c = a % b;
        if (c == a) throw new IllegalStateException();
    }

    public void noRetNotConst(Point a, Point b) {
        Point c = new Point(a.x / b.x, a.y / b.y);
        if (c.x + c.y == a.x) throw new IllegalStateException();
    }

    public int withRetConst(int a, int b) {
        if (a * b == -10) throw new IllegalStateException();
        int c = a % b;
        if (c == a) throw new IllegalStateException();
        return c;
    }

    public Point withRetNotConst(Point a, Point b) {
        Point c = new Point(a.x / b.x, a.y / b.y);
        if (c.x + c.y == a.x) throw new IllegalStateException();
        return c;
    }

}
