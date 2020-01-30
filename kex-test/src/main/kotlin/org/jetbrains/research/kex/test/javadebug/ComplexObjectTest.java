package org.jetbrains.research.kex.test.javadebug;

public class ComplexObjectTest {
    static class Point {
        int x;
        int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }

    static class Line {
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

    public void testLine(Line line) {
        if (line == null) return;
        if (line.start != null) {
            System.out.println("start is valid");
        }
        if (line.end != null) {
            System.out.println("end is valid");
        }
        if (line.start.equals(line.end)) {
            System.out.println("line is actually point");
        }
    }

}
