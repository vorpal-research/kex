package org.jetbrains.research.kex.test.generation;

import static org.jetbrains.research.kex.Intrinsics.*;

import java.util.Objects;

public class BasicJavaObjectGeneration {
    public static class Point {
        int x;
        int y;
        int z;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        final public void setZ(int z) {
            this.z = z;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
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

    public void checkPoint(Point p1, Point p2) {
        if (p1.x > p2.x) {
            kexAssert();
        }
        if (p1.z < p2.z) {
            kexAssert();
        }
        kexAssert();
    }

    public void testLine(Line line) {
        if (line == null) return;
        if (line.start != null) {
            kexAssert();
        }
        if (line.end != null) {
            kexAssert();
        }
        if (Objects.equals(line.start, line.end)) {
            kexAssert();
        }
        kexAssert();
    }
}
