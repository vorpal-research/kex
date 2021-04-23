package org.jetbrains.research.kex.test;

import static org.jetbrains.research.kex.Intrinsics.kexAssert;
import static org.jetbrains.research.kex.Intrinsics.kexUnreachable;

public class ObjectJavaTests {
    public static class PointJava {
        int x;
        int y;
        int z;

        public PointJava(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
    public static class LineJava {
        PointJava start;
        PointJava end;

        public LineJava(PointJava start, PointJava end) {
            this.start = start;
            this.end = end;
        }
    }

    public static class DoublePointJava {
        double x;
        double y;
        double z;

        DoublePointJava(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public void simplePointCheck() {
        PointJava zero = new PointJava(0, 0, 0);
        zero.x = 0;
        zero.y = 0;

        PointJava ten = new PointJava(10, 10, 0);
        ten.x = 10;
        ten.y = 10;

        if (ten.x > zero.x) {
            kexAssert();
        } else {
            kexUnreachable();
        }
    }

    public LineJava testObjects(PointJava a , DoublePointJava b) {
        double xs = b.x - a.x;
        double ys = b.y - a.y;
        double zs = b.z - a.z;

        double xe = a.x + b.x;
        double ye = a.y + b.y;
        double ze = a.z + b.z;

        PointJava start = new PointJava((int) xs, (int) ys, (int) zs);
        PointJava end = new PointJava((int) xe, (int) ye, (int) ze);
        LineJava result = new LineJava(start, end);
        kexAssert();
        return result;
    }

    public void testLines(LineJava a, LineJava b) {
        if (a == b) {
            System.out.println("Same");
        } else {
            a.start = b.end;
        }
    }
}
