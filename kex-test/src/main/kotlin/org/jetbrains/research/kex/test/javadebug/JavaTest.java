package org.jetbrains.research.kex.test.javadebug;

public class JavaTest {
    public static class Point {
        int x;
        int y;
        int z;

        public Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        final void setY(int y) {
            this.y = y;
        }

        final void setZ(int z) {
            this.z = z;
        }

        @Override
        public String toString() {
            return "Point(" + x + ", " + y + ", " + z + ")";
        }
    }

    public static class Line {
        Point start;
        Point end;

        public Line(Point start, Point end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "{" + start + ", " + end + "}";
        }
    }

    public void checkPoint(Point p1) {
        if (p1.x == 3 && p1.y == -1 && p1.z == 42) {
            System.out.println("Success");
        }
    }

    public void checkLine(Line l1) {
        if (l1.start != null && l1.start.x == 1) {
            System.out.println("Success");
        }
    }
}
