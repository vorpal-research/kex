package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

@SuppressWarnings("ALL")
public class PrimitiveConcolicTests {
    public void testInt(int a) {
        if (a > 0) {
            AssertIntrinsics.kexAssert(true);
        } else if (a == 0) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testDouble(double d) {
        if (d > 0.0) {
            AssertIntrinsics.kexAssert(true);
        } else if (d == 0.0) {
            AssertIntrinsics.kexAssert(true);
        } else if (d < 0.0) {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testPoints(Point a, Point b) {
        int ax = a.getX();
        int bx = b.getX();
        int ay = a.getY();
        int by = b.getY();
        int mx = Math.max(ax, bx);
        int my = Math.min(ay, by);
        if (mx < my) {
            AssertIntrinsics.kexAssert(true);
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testCharMatrix(char[][] a) {
        if (a.length > 10) {
            if (a[0].length < 2) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }

    public void testStringMatrix(String[][] a) {
        if (a.length > 10) {
            if (a[0].length < 2) {
                AssertIntrinsics.kexAssert(true);
            } else {
                AssertIntrinsics.kexAssert(true);
            }
        } else {
            AssertIntrinsics.kexAssert(true);
        }
    }
}
