package org.vorpal.research.kex.test.concolic;

@SuppressWarnings("ALL")
public class AssertAndExceptionsTests {

    int instanceValue1 = 10;
    Point instanceValue2 = new Point(8, 42);

    public void noReturnWithConst(int a) {
        int b;
        if (a > 10)
            b = 100;
        else if (a < -2)
            b = 10;
        //else throw new IllegalStateException();
        //if (a % b == 13) throw new IllegalStateException();
    }

    public void noReturnWithObject(Point a, Point b) {
        //if (a.x == b.y) throw new IllegalStateException();
        Point c = new Point(a.y, b.x);
        //if (c.x - c.y == 13) throw new IllegalStateException();
    }

    public void noReturnWithConstAndInst(int a) {
        int b;
        if (a > 10)
            b = instanceValue1;
        else if (a < -2)
            b = instanceValue1 + 7;
        //else throw new IllegalStateException();
    }

    public void noReturnWithObjectAndInst(Point a, Point b) {
        //if (a.x == instanceValue2.y) throw new IllegalStateException();
        Point c = new Point(a.y, instanceValue2.x);
        Point d = new Point(b.x, instanceValue2.y);
    }

    public int withReturnWithConst(int a) {
        int b;
        if (a % 13 == 1)
            b = 101;
        /*else if (a % 26 == 1)
            throw new IllegalStateException();*/
        else b = 13;

        //if (b % 8 == 5) throw new IllegalStateException();
        return b * 7;
    }

    public Point withReturnWithObject(Point a, Point b) {
        //if (a.x % 13 == b.y % 31) throw new IllegalStateException();
        Point c = new Point(b.x, a.y);
        //if (c.x == 8) throw new IllegalStateException();
        return c;
    }

}
