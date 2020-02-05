package org.jetbrains.research.kex.test.generation;

import org.jetbrains.research.kex.test.Intrinsics;

import java.util.Objects;

public class BasicJavaObjectGeneration {
    static class Point {
        int x;
        int y;
        int z;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        final void setZ(int z) {
            this.z = z;
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

    public void checkPoint(Point p1, Point p2) {
        if (p1.x > p2.x) {
            Intrinsics.assertReachable();
        }
        if (p1.z < p2.z) {
            Intrinsics.assertReachable();
        }
        Intrinsics.assertReachable();
    }

    public void testLine(Line line) {
        if (line == null) return;
        if (line.start != null) {
            Intrinsics.assertReachable();
        }
        if (line.end != null) {
            Intrinsics.assertReachable();
        }
        if (Objects.equals(line.start, line.end)) {
            Intrinsics.assertReachable();
        }
        Intrinsics.assertReachable();
    }
}
