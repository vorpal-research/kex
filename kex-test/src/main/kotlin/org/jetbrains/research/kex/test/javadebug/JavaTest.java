package org.jetbrains.research.kex.test.javadebug;

import java.util.List;

class Point {
    int x;
    int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}

public abstract class JavaTest {
    public static void testList(List<Point> points) {
        if (points.get(0).x == 10) {
            throw new IllegalStateException();
        }
    }
}