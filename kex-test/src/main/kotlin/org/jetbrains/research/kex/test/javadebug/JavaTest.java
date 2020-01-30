package org.jetbrains.research.kex.test.javadebug;

public class JavaTest {
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
            return "Point(" + x + ", " + y + ", " + z + ")";
        }
    }

    public void checkPoint(Point p1, Point p2) {
        if (p1.x > p2.x) {
            System.out.println("Success");
        }
        if (p1.z < p2.z) {
            System.out.println("Double success");
        }
        System.out.println("end");
    }
}
