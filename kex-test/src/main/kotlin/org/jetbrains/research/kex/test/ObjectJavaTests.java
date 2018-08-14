package org.jetbrains.research.kex.test;

class PointJava {
    int x = 0;
    int y = 0;
}

public class ObjectJavaTests {
    public void simplePointCheck() {
        PointJava zero = new PointJava();
        zero.x = 0;
        zero.y = 0;

        PointJava ten = new PointJava();
        ten.x = 10;
        ten.y = 10;

        if (ten.x > zero.x) {
            Intrinsics.assertReachable();
        } else {
            Intrinsics.assertUnreachable();
        }
    }
}
