package org.jetbrains.research.kex.test.javadebug;

class Point {
    private int x;
    private int y;

    private Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point point(int x, int y) {
        return new Point(x ,y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}

public class JavaTest {

    public int test(Point p) {
        if (p.getX() == 10) {
            return -1;
        }
        return p.getY();
    }
}
