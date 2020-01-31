package org.jetbrains.research.kex.test.javadebug;

public class JavaTest {
    static class Point {
        int x;
        int y;
        int z;

        public Point(int x) {
            this.x = x;
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

    public void checkPoint(Point p1) {
        if (p1.x == 3 && p1.y == -1 && p1.z == 42) {
            System.out.println("Success");
        }
    }
}
