package org.vorpal.research.kex.test.concolic;

public class AssertAndExceptionsTests2 {

    int instanceValue1 = 10;
    Point instanceValue2 = new Point(8, 42);

    public void noRetConstNoArgs() {
        int a = instanceValue1 % 31;
        int b = instanceValue1 % 13;
        if (a * b == 10) throw new IllegalStateException();
        if (a - b == -5) throw new IllegalStateException();
    }

    public int withRetConstNoArgs() {
        int a = instanceValue1 % 31;
        int b = instanceValue1 % 13;
        if (a * b == 10) throw new IllegalStateException();
        if (a - b == -5) throw new IllegalStateException();
        return a + b;
    }

    public void noRetNotConstNoArgs() {
        Point a = new Point(instanceValue1, instanceValue2.y);
        Point b = new Point(instanceValue2.x, instanceValue1);
        if (a.x + a.y - 100 == 0) throw new IllegalStateException();
        if (b.y - b.x == a.x + a.y) throw new IllegalStateException();
    }

    public Point withRetNotConstNoArgs() {
        Point a = new Point(instanceValue1, instanceValue2.y);
        Point b = new Point(instanceValue2.x, instanceValue1);
        if (a.x + a.y - 100 == 0) throw new IllegalStateException();
        if (b.y - b.x == a.x + a.y) throw new IllegalStateException();
        return a;
    }

    public void noRetConstWithArgs(int a, int b) {
        if (a * b == -10) throw new IllegalStateException();
        int c = a % b - instanceValue1;
        if (c == a) throw new IllegalStateException();
    }

    public void noRetNotConstWithArgs(Point a, Point b) {
        a.x = instanceValue2.x - instanceValue2.y;
        Point c = new Point(a.x / b.x, a.y / b.y);
        if (c.x + c.y == a.x) throw new IllegalStateException();
    }

    public int withRetConstWithArgs(int a, int b) {
        if (a * b == -10) throw new IllegalStateException();
        int c = a % b - instanceValue1;
        if (c == a) throw new IllegalStateException();
        return c;
    }

    public Point withRetNotConstWithArgs(Point a, Point b) {
        a.x = instanceValue2.x - instanceValue2.y;
        Point c = new Point(a.x / b.x, a.y / b.y);
        if (c.x + c.y == a.x) throw new IllegalStateException();
        return c;
    }
}
