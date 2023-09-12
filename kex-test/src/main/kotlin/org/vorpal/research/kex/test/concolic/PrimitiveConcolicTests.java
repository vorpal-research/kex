package org.vorpal.research.kex.test.concolic;

import org.vorpal.research.kex.intrinsics.AssertIntrinsics;

@SuppressWarnings("ALL")
public class PrimitiveConcolicTests {
    public int testInt(int a) {
        a = 7;
        return a;
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

    public int testPoints(Point a, Point b) {
        a.x = 10;
        a.y = 17;
        return a.y;
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
