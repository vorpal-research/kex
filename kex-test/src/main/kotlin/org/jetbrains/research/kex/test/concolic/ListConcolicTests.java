package org.jetbrains.research.kex.test.concolic;

import java.util.List;

public class ListConcolicTests {
    public static void testList(List<Point> points) {
        if (points.get(0).x == 10) {
            throw new IllegalStateException();
        }
    }
}
