package org.jetbrains.research.kex.test.debug;

public class Test {
    class PointJava {
        int x;
        int y;
        int z;

        PointJava(int x, int y, int z) {
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
            System.out.println("reachable");
        } else {
            System.out.println("unreachable");
        }
    }
}
